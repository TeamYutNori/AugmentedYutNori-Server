package com.teamyutnori.yutnori.reconnect;

import com.teamyutnori.yutnori.config.GameProperties;
import com.teamyutnori.yutnori.ws.RoomBroadcaster;
import com.teamyutnori.yutnori.ws.RoomSessionRegistry;
import com.teamyutnori.yutnori.ws.WsMessageContext;
import com.teamyutnori.yutnori.ws.dto.MessageType;
import com.teamyutnori.yutnori.ws.dto.WsMessages.PlayerDisconnectedMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketSession;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ConnectionCleanupTest {

    private static final String ROOM = "ABC123";
    private static final Duration GRACE = Duration.ofMillis(100);

    private ThreadPoolTaskScheduler scheduler;
    private RoomBroadcaster broadcaster;
    private RoomSessionRegistry registry;
    private List<Object> events;
    private ConnectionCleanup cleanup;

    private final WsMessageContext p1 = new WsMessageContext(null, ROOM, "p1", null);
    private final WsMessageContext p2 = new WsMessageContext(null, ROOM, "p2", null);

    @BeforeEach
    void setUp() {
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.initialize();
        broadcaster = mock(RoomBroadcaster.class);
        registry = mock(RoomSessionRegistry.class);
        when(registry.find(any(), any())).thenReturn(Optional.empty());   // 기본: 연결 없음
        events = new CopyOnWriteArrayList<>();

        GameProperties properties = new GameProperties(
                2, 2, Duration.ofSeconds(30), 3, Duration.ofSeconds(20), GRACE);
        cleanup = new ConnectionCleanup(broadcaster, registry, properties, scheduler,
                Clock.systemUTC(), events::add);
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdown();
    }

    // 끊기면 방에 PLAYER_DISCONNECTED를 알리고 대기 상태가 된다
    @Test
    void disconnect() {
        cleanup.onDisconnected(p1);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(broadcaster).broadcast(eq(ROOM), eq(MessageType.PLAYER_DISCONNECTED), captor.capture());
        assertThat(((PlayerDisconnectedMessage) captor.getValue()).playerId()).isEqualTo("p1");
        assertThat(cleanup.isWaiting(ROOM, "p1")).isTrue();
    }

    // 대기 시간이 지나면 DisconnectTimeoutEvent가 발행된다
    @Test
    void timeout() throws InterruptedException {
        cleanup.onDisconnected(p1);

        waitFor(1, Duration.ofSeconds(1));

        assertThat(events).hasSize(1);
        DisconnectTimeoutEvent event = (DisconnectTimeoutEvent) events.get(0);
        assertThat(event.roomCode()).isEqualTo(ROOM);
        assertThat(event.playerId()).isEqualTo("p1");
        assertThat(cleanup.isWaiting(ROOM, "p1")).isFalse();
    }

    // 대기 중에 다시 연결되면 이벤트 없이 PLAYER_RECONNECTED를 알린다
    @Test
    void reconnect() throws InterruptedException {
        cleanup.onDisconnected(p1);
        cleanup.onConnected(p1);

        Thread.sleep(300);

        assertThat(events).isEmpty();
        verify(broadcaster).broadcast(eq(ROOM), eq(MessageType.PLAYER_RECONNECTED), any());
        assertThat(cleanup.isWaiting(ROOM, "p1")).isFalse();
    }

    // 처음 연결은 아무것도 알리지 않는다
    @Test
    void firstConnect() {
        cleanup.onConnected(p1);

        verifyNoInteractions(broadcaster);
    }

    // 재접속 후 옛 세션 종료가 늦게 오면 (새 세션이 남아 있으면) 무시한다
    @Test
    void lateClose() {
        when(registry.find(ROOM, "p1")).thenReturn(Optional.of(mock(WebSocketSession.class)));

        cleanup.onDisconnected(p1);

        verifyNoInteractions(broadcaster);
        assertThat(cleanup.isWaiting(ROOM, "p1")).isFalse();
    }

    // 스스로 나가서 cancel하면 이벤트도, 재접속 알림도 없다
    @Test
    void cancel() throws InterruptedException {
        cleanup.onDisconnected(p1);
        cleanup.cancel(ROOM, "p1");

        Thread.sleep(300);

        assertThat(events).isEmpty();
        verify(broadcaster, never()).broadcast(any(), eq(MessageType.PLAYER_RECONNECTED), any());
    }

    // clearRoom 하면 그 방의 모든 대기가 취소된다
    @Test
    void clearRoom() throws InterruptedException {
        cleanup.onDisconnected(p1);
        cleanup.onDisconnected(p2);
        cleanup.clearRoom(ROOM);

        Thread.sleep(300);

        assertThat(events).isEmpty();
        assertThat(cleanup.isWaiting(ROOM, "p1")).isFalse();
        assertThat(cleanup.isWaiting(ROOM, "p2")).isFalse();
    }

    // 플레이어마다 따로 기다린다
    @Test
    void separatePlayer() throws InterruptedException {
        cleanup.onDisconnected(p1);
        cleanup.onDisconnected(p2);
        cleanup.onConnected(p1);

        waitFor(1, Duration.ofSeconds(1));
        Thread.sleep(100);

        assertThat(events).hasSize(1);
        assertThat(((DisconnectTimeoutEvent) events.get(0)).playerId()).isEqualTo("p2");
    }

    private void waitFor(int count, Duration timeout) throws InterruptedException {
        long end = System.currentTimeMillis() + timeout.toMillis();
        while (events.size() < count && System.currentTimeMillis() < end) {
            Thread.sleep(10);
        }
    }
}