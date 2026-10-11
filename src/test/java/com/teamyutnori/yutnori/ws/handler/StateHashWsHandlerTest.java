package com.teamyutnori.yutnori.ws.handler;

import com.teamyutnori.yutnori.common.ForbiddenException;
import com.teamyutnori.yutnori.common.NotFoundException;
import com.teamyutnori.yutnori.game.model.GameSession;
import com.teamyutnori.yutnori.game.repository.GameSessionRepository;
import com.teamyutnori.yutnori.game.service.StateDesyncEvent;
import com.teamyutnori.yutnori.game.service.StateHashVerifier;
import com.teamyutnori.yutnori.ws.WsMessageContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StateHashWsHandlerTest {

    private static final String ROOM = "ROOM1";

    private StateHashVerifier verifier;
    private GameSessionRepository repository;
    private ApplicationEventPublisher publisher;
    private StateHashWsHandler handler;
    private final ObjectMapper mapper = JsonMapper.builder().build();

    @BeforeEach
    void setUp() {
        verifier = new StateHashVerifier();
        repository = mock(GameSessionRepository.class);
        publisher = mock(ApplicationEventPublisher.class);
        handler = new StateHashWsHandler(verifier, repository, publisher, mapper);
    }

    // 2인 게임 세션을 만든다 (p1 = 팀 0, p2 = 팀 1)
    private void twoPlayerGame() {
        GameSession session = mock(GameSession.class);
        when(session.getPlayerTeams()).thenReturn(Map.of("p1", 0, "p2", 1));
        when(session.teamOf("p1")).thenReturn(Optional.of(0));
        when(session.teamOf("p2")).thenReturn(Optional.of(1));
        when(session.teamOf("stranger")).thenReturn(Optional.empty());
        when(repository.find(ROOM)).thenReturn(Optional.of(session));
    }

    private void send(String playerId, long seq, String hash) {
        WsMessageContext ctx = new WsMessageContext(null, ROOM, playerId, playerId);
        handler.handle(ctx, mapper.readTree("{\"seq\":" + seq + ",\"stateHash\":\"" + hash + "\"}"));
    }

    // 2인 게임: 2명이 같은 해시를 보내면 비교가 끝나고 대기 목록에서 빠진다 (최대 인원 4를 기다리지 않음)
    @Test
    void twoPlayersMatch() {
        twoPlayerGame();
        send("p1", 1, "aaaaaaaaaaaaaaaa");
        send("p2", 1, "aaaaaaaaaaaaaaaa");

        assertEquals(0, verifier.pendingCount(ROOM));
        verify(publisher, never()).publishEvent(any());
    }

    // 2인 게임: 해시가 다르면 2명째에서 바로 DESYNC 이벤트 발행
    @Test
    void twoPlayersDesync() {
        twoPlayerGame();
        send("p1", 1, "aaaaaaaaaaaaaaaa");
        send("p2", 1, "bbbbbbbbbbbbbbbb");

        verify(publisher).publishEvent(any(StateDesyncEvent.class));
    }

    // 게임이 없는 방이면 GAME_NOT_FOUND
    @Test
    void noGame() {
        when(repository.find(ROOM)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> send("p1", 1, "aaaaaaaaaaaaaaaa"));
    }

    // 게임에 없는 플레이어면 PLAYER_NOT_IN_GAME
    @Test
    void notInGame() {
        twoPlayerGame();
        assertThrows(ForbiddenException.class, () -> send("stranger", 1, "aaaaaaaaaaaaaaaa"));
    }
}