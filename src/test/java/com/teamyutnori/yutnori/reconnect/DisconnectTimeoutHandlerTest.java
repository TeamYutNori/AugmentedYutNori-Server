package com.teamyutnori.yutnori.reconnect;

import com.teamyutnori.yutnori.ws.RoomBroadcaster;
import com.teamyutnori.yutnori.ws.dto.MessageType;
import com.teamyutnori.yutnori.ws.dto.WsMessages.GameEndReason;
import com.teamyutnori.yutnori.ws.dto.WsMessages.GameEndedMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DisconnectTimeoutHandlerTest {

    private static final String ROOM = "ABC123";

    // 게임 쪽 가짜 구현: 진행 여부, 팀 매핑, 종료 기록만 한다
    static class FakeGame implements GameEndPort {
        boolean playing = true;
        final Map<String, Integer> teams = new HashMap<>(Map.of("p0", 0, "p1", 1));
        Integer endedWinner;

        public boolean isPlaying(String roomCode) { return playing; }
        public Optional<Integer> findTeam(String roomCode, String playerId) {
            return Optional.ofNullable(teams.get(playerId));
        }
        public void endGame(String roomCode, int winnerTeam) {
            endedWinner = winnerTeam;
            playing = false;
        }
    }

    private FakeGame game;
    private RoomBroadcaster broadcaster;
    private TurnTimerService turnTimerService;
    private ConnectionCleanup connectionCleanup;
    private DisconnectTimeoutHandler handler;

    @BeforeEach
    void setUp() {
        game = new FakeGame();
        broadcaster = mock(RoomBroadcaster.class);
        turnTimerService = mock(TurnTimerService.class);
        connectionCleanup = mock(ConnectionCleanup.class);
        handler = new DisconnectTimeoutHandler(Optional.of(game), broadcaster, turnTimerService, connectionCleanup);
    }

    // 끊긴 팀의 상대가 이기고, GAME_ENDED(DISCONNECTED)가 방송된다
    @Test
    void forfeit() {
        handler.onDisconnectTimeout(new DisconnectTimeoutEvent(ROOM, "p1"));

        assertThat(game.endedWinner).isEqualTo(0);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(broadcaster).broadcast(eq(ROOM), eq(MessageType.GAME_ENDED), captor.capture());
        GameEndedMessage message = (GameEndedMessage) captor.getValue();
        assertThat(message.winnerTeam()).isEqualTo(0);
        assertThat(message.reason()).isEqualTo(GameEndReason.DISCONNECTED);
    }

    // 게임이 끝나면 턴 타이머와 남은 재접속 대기를 정리한다
    @Test
    void cleanup() {
        handler.onDisconnectTimeout(new DisconnectTimeoutEvent(ROOM, "p0"));

        verify(turnTimerService).cancel(ROOM);
        verify(connectionCleanup).clearRoom(ROOM);
    }

    // 게임 중이 아니면 (로비, 이미 끝남) 아무것도 하지 않는다
    @Test
    void notPlaying() {
        game.playing = false;

        handler.onDisconnectTimeout(new DisconnectTimeoutEvent(ROOM, "p1"));

        assertThat(game.endedWinner).isNull();
        verifyNoInteractions(broadcaster, turnTimerService, connectionCleanup);
    }

    // 둘 다 끊겨도 게임 종료는 한 번만 처리된다
    @Test
    void bothDisconnected() {
        handler.onDisconnectTimeout(new DisconnectTimeoutEvent(ROOM, "p1"));
        handler.onDisconnectTimeout(new DisconnectTimeoutEvent(ROOM, "p0"));

        assertThat(game.endedWinner).isEqualTo(0);
        verify(broadcaster, times(1)).broadcast(eq(ROOM), eq(MessageType.GAME_ENDED), org.mockito.ArgumentMatchers.any());
    }

    // 팀을 모르는 플레이어면 처리하지 않는다
    @Test
    void unknownPlayer() {
        handler.onDisconnectTimeout(new DisconnectTimeoutEvent(ROOM, "ghost"));

        assertThat(game.endedWinner).isNull();
        verifyNoInteractions(broadcaster);
    }

    // GameEndPort 구현이 없으면 건너뛴다 (서버는 정상 동작)
    @Test
    void noPort() {
        DisconnectTimeoutHandler noPortHandler = new DisconnectTimeoutHandler(
                Optional.empty(), broadcaster, turnTimerService, connectionCleanup);

        noPortHandler.onDisconnectTimeout(new DisconnectTimeoutEvent(ROOM, "p1"));

        verifyNoInteractions(broadcaster);
    }
}