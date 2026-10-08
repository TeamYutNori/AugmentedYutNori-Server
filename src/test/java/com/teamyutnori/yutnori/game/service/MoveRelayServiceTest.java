package com.teamyutnori.yutnori.game.service;

import com.teamyutnori.yutnori.common.ConflictException;
import com.teamyutnori.yutnori.common.ForbiddenException;
import com.teamyutnori.yutnori.common.InvalidRequestException;
import com.teamyutnori.yutnori.game.dto.GameMessages.MoveAppliedMessage;
import com.teamyutnori.yutnori.game.dto.GameRequests.MoveRequest;
import com.teamyutnori.yutnori.game.model.GamePhase;
import com.teamyutnori.yutnori.game.model.GameSession;
import com.teamyutnori.yutnori.game.model.GameSetup;
import com.teamyutnori.yutnori.game.repository.GameSessionRepository;
import com.teamyutnori.yutnori.game.yut.YutResult;
import com.teamyutnori.yutnori.reconnect.CommandLog;
import com.teamyutnori.yutnori.ws.RoomBroadcaster;
import com.teamyutnori.yutnori.ws.dto.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MoveRelayServiceTest {

    private static final String ROOM = "ROOM";
    private static final String HASH = "0123456789abcdef";

    private GameSession session;
    private GameService gameService;
    private RoomBroadcaster broadcaster;
    private CommandLog commandLog;
    private MoveRelayService service;

    @BeforeEach
    void setUp() {
        // 팀 0(p0)의 차례, 개(2칸) 결과를 가진 상태
        session = new GameSession(ROOM, new GameSetup(2, 2, "default", false), Map.of("p0", 0, "p1", 1));
        session.setPhase(GamePhase.PLAYING);
        session.setCurrentTeam(0);
        session.getStoredResults().add(YutResult.Gae);

        GameSessionRepository repository = mock(GameSessionRepository.class);
        when(repository.find(ROOM)).thenReturn(Optional.of(session));
        gameService = mock(GameService.class);
        broadcaster = mock(RoomBroadcaster.class);
        when(broadcaster.broadcast(any(), any(), any())).thenReturn(5L);
        commandLog = new CommandLog();

        service = new MoveRelayService(repository, gameService, broadcaster, commandLog);
    }

    // 개(2칸)로 내 말(0번)을 옮기는 정상 요청
    private MoveRequest validMove() {
        return new MoveRequest(0, 2, 7, 0, false, false, false, HASH);
    }

    // 정상 이동: MOVE_APPLIED 방송 → CommandLog 기록 → GameService 통보
    @Test
    void move() {
        service.move(ROOM, "p0", validMove());

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(broadcaster).broadcast(eq(ROOM), eq(MessageType.MOVE_APPLIED), captor.capture());
        MoveAppliedMessage applied = (MoveAppliedMessage) captor.getValue();
        assertThat(applied.team()).isZero();
        assertThat(applied.pieceId()).isZero();
        assertThat(applied.destinationNodeId()).isEqualTo(7);
        assertThat(applied.stateHash()).isEqualTo(HASH);

        assertThat(commandLog.lastSeq(ROOM)).isEqualTo(5);
        verify(gameService).onMoveApplied(ROOM, 0, 2, 0, false, false);
    }

    // MOVE_APPLIED를 먼저 보내고 그다음 GameService를 부른다 (TURN_CHANGED가 먼저 가지 않도록)
    @Test
    void order() {
        service.move(ROOM, "p0", validMove());

        InOrder inOrder = inOrder(broadcaster, gameService);
        inOrder.verify(broadcaster).broadcast(eq(ROOM), eq(MessageType.MOVE_APPLIED), any());
        inOrder.verify(gameService).onMoveApplied(anyString(), anyInt(), anyInt(), anyInt(), anyBoolean(), anyBoolean());
    }

    // 상대 차례면 NOT_YOUR_TURN, 아무것도 보내지 않는다
    @Test
    void notYourTurn() {
        assertThatThrownBy(() -> service.move(ROOM, "p1", new MoveRequest(100, 2, 7, 0, false, false, false, HASH)))
                .isInstanceOf(ForbiddenException.class)
                .extracting("code").isEqualTo("NOT_YOUR_TURN");
        verifyNoInteractions(broadcaster, gameService);
    }

    // 참가자가 아니면 PLAYER_NOT_IN_GAME
    @Test
    void notInGame() {
        assertThatThrownBy(() -> service.move(ROOM, "ghost", validMove()))
                .isInstanceOf(ForbiddenException.class)
                .extracting("code").isEqualTo("PLAYER_NOT_IN_GAME");
    }

    // 게임 진행 중이 아니면 INVALID_PHASE
    @Test
    void invalidPhase() {
        session.setPhase(GamePhase.AUGMENT_SELECT);

        assertThatThrownBy(() -> service.move(ROOM, "p0", validMove()))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("INVALID_PHASE");
    }

    // 상대 팀 말이거나 없는 번호의 말이면 INVALID_PIECE
    @Test
    void invalidPiece() {
        assertThatThrownBy(() -> service.move(ROOM, "p0", new MoveRequest(100, 2, 7, 0, false, false, false, HASH)))
                .isInstanceOf(InvalidRequestException.class)
                .extracting("code").isEqualTo("INVALID_PIECE");
        assertThatThrownBy(() -> service.move(ROOM, "p0", new MoveRequest(5, 2, 7, 0, false, false, false, HASH)))
                .isInstanceOf(InvalidRequestException.class)
                .extracting("code").isEqualTo("INVALID_PIECE");   // 팀당 2개라 순번 5는 없음
    }

    // 가지고 있지 않은 윷 결과로 이동하면 INVALID_MOVE_RESULT, 아무것도 보내지 않는다
    @Test
    void invalidMoveCount() {
        assertThatThrownBy(() -> service.move(ROOM, "p0", new MoveRequest(0, 5, 7, 0, false, false, false, HASH)))
                .isInstanceOf(InvalidRequestException.class)
                .extracting("code").isEqualTo("INVALID_MOVE_RESULT");
        verifyNoInteractions(broadcaster, gameService);
    }

    // 빽도(-1)도 저장된 결과에 있으면 통과한다
    @Test
    void backDo() {
        session.getStoredResults().add(YutResult.BackDo);

        service.move(ROOM, "p0", new MoveRequest(1, -1, 3, 0, false, false, true, HASH));

        verify(gameService).onMoveApplied(ROOM, 0, -1, 0, false, true);
    }

    // 잡은 말 수가 음수면 INVALID_MOVE_REPORT
    @Test
    void invalidReport() {
        assertThatThrownBy(() -> service.move(ROOM, "p0", new MoveRequest(0, 2, 7, -1, false, false, false, HASH)))
                .isInstanceOf(InvalidRequestException.class)
                .extracting("code").isEqualTo("INVALID_MOVE_REPORT");
    }
}