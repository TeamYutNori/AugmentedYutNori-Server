package com.teamyutnori.yutnori.ws.handler;

import com.teamyutnori.yutnori.common.ForbiddenException;
import com.teamyutnori.yutnori.common.NotFoundException;
import com.teamyutnori.yutnori.game.model.GamePhase;
import com.teamyutnori.yutnori.game.model.GameSession;
import com.teamyutnori.yutnori.game.repository.GameSessionRepository;
import com.teamyutnori.yutnori.game.service.StateDesyncEvent;
import com.teamyutnori.yutnori.game.service.StateHashVerifier;
import com.teamyutnori.yutnori.ws.RoomSessionRegistry;
import com.teamyutnori.yutnori.ws.WsMessageContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StateHashWsHandlerTest {

    private static final String ROOM = "ROOM1";
    private static final String HASH_A = "aaaaaaaaaaaaaaaa";
    private static final String HASH_B = "bbbbbbbbbbbbbbbb";

    private StateHashVerifier verifier;
    private GameSessionRepository repository;
    private RoomSessionRegistry registry;
    private ApplicationEventPublisher publisher;
    private GameSession session;
    private StateHashWsHandler handler;
    private final ObjectMapper mapper = JsonMapper.builder().build();

    @BeforeEach
    void setUp() {
        verifier = new StateHashVerifier();
        repository = mock(GameSessionRepository.class);
        registry = mock(RoomSessionRegistry.class);
        publisher = mock(ApplicationEventPublisher.class);
        handler = new StateHashWsHandler(verifier, repository, registry, publisher, mapper);

        // 3인 게임: p0 = 팀 0, p1 = 팀 1, p2 = 팀 2. 기본은 모두 연결, 아무도 안 나감
        session = mock(GameSession.class);
        when(session.getPlayerTeams()).thenReturn(Map.of("p0", 0, "p1", 1, "p2", 2));
        when(session.isRemoved(anyInt())).thenReturn(false);
        when(repository.find(ROOM)).thenReturn(Optional.of(session));
        when(registry.find(eq(ROOM), any())).thenReturn(Optional.of(mock(WebSocketSession.class)));
        when(session.getPhase()).thenReturn(GamePhase.PLAYING);
    }

    private void disconnect(String playerId) {
        when(registry.find(ROOM, playerId)).thenReturn(Optional.empty());
    }

    private void send(String playerId, long seq, String hash) {
        WsMessageContext ctx = new WsMessageContext(null, ROOM, playerId, playerId);
        handler.handle(ctx, mapper.readTree("{\"seq\":" + seq + ",\"stateHash\":\"" + hash + "\"}"));
    }

    // 전원 연결: 3명이 다 보내야 비교가 끝난다
    @Test
    void allConnected() {
        send("p0", 1, HASH_A);
        send("p1", 1, HASH_A);
        assertEquals(1, verifier.pendingCount(ROOM));

        send("p2", 1, HASH_A);
        assertEquals(0, verifier.pendingCount(ROOM));
    }

    // 1명 끊김: 연결된 2명만 보내면 비교가 끝난다 (자동 진행 중에도 대기가 쌓이지 않음)
    @Test
    void oneDisconnected() {
        disconnect("p2");

        send("p0", 1, HASH_A);
        send("p1", 1, HASH_A);

        assertEquals(0, verifier.pendingCount(ROOM));
        verify(publisher, never()).publishEvent(any());
    }

    // 나간 팀은 세지 않는다
    @Test
    void removedTeam() {
        when(session.isRemoved(2)).thenReturn(true);

        send("p0", 1, HASH_A);
        send("p1", 1, HASH_A);

        assertEquals(0, verifier.pendingCount(ROOM));
    }

    // 연결된 사람끼리 해시가 다르면 DESYNC
    @Test
    void desync() {
        disconnect("p2");

        send("p0", 1, HASH_A);
        send("p1", 1, HASH_B);

        verify(publisher).publishEvent(any(StateDesyncEvent.class));
    }

    // 게임이 없으면 (게임 종료 직후 세션 정리 등) 에러 없이 무시
    @Test
    void noGame() {
        when(repository.find(ROOM)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> send("p0", 1, HASH_A));
        assertEquals(0, verifier.pendingCount(ROOM));
    }

    // 끝난 게임이면 무시
    @Test
    void ended() {
        when(session.getPhase()).thenReturn(GamePhase.ENDED);

        assertDoesNotThrow(() -> send("p0", 1, HASH_A));
        assertEquals(0, verifier.pendingCount(ROOM));
    }

    // 참가자가 아니거나 이미 나간 사람이면 무시
    @Test
    void notInGame() {
        assertDoesNotThrow(() -> send("stranger", 1, HASH_A));

        when(session.isRemoved(0)).thenReturn(true);
        assertDoesNotThrow(() -> send("p0", 1, HASH_A));

        assertEquals(0, verifier.pendingCount(ROOM));
    }

    // seq가 0 이하면 무시
    @Test
    void invalidSeq() {
        assertDoesNotThrow(() -> send("p0", 0, HASH_A));
        assertEquals(0, verifier.pendingCount(ROOM));
    }
    // 연결된 사람이 혼자면 비교하지 않고 넘어간다 (대기 목록에도 안 쌓임)
    @Test
    void alone() {
        disconnect("p1");
        disconnect("p2");

        send("p0", 1, HASH_A);

        assertEquals(0, verifier.pendingCount(ROOM));
        verify(publisher, never()).publishEvent(any());
    }
}