package com.teamyutnori.yutnori.game.service;

import com.teamyutnori.yutnori.common.ConflictException;
import com.teamyutnori.yutnori.common.ForbiddenException;
import com.teamyutnori.yutnori.common.InvalidRequestException;
import com.teamyutnori.yutnori.config.GameProperties;
import com.teamyutnori.yutnori.game.augment.AugmentDraftService;
import com.teamyutnori.yutnori.game.augment.RethrowValidator;
import com.teamyutnori.yutnori.game.board.TestBoards;
import com.teamyutnori.yutnori.game.dto.GameMessages.MoveAppliedMessage;
import com.teamyutnori.yutnori.game.dto.GameRequests.MoveRequest;
import com.teamyutnori.yutnori.game.model.GamePhase;
import com.teamyutnori.yutnori.game.model.GameSession;
import com.teamyutnori.yutnori.game.model.GameSetup;
import com.teamyutnori.yutnori.game.repository.GameSessionRepository;
import com.teamyutnori.yutnori.game.yut.YutResult;
import com.teamyutnori.yutnori.game.yut.YutThrowService;
import com.teamyutnori.yutnori.reconnect.CommandLog;
import com.teamyutnori.yutnori.reconnect.TurnTimerService;
import com.teamyutnori.yutnori.ws.RoomBroadcaster;
import com.teamyutnori.yutnori.ws.dto.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// GameService.move: MOVE 검증 → 판 적용 → MOVE_APPLIED(+CommandLog) → 턴 처리
// (예전 MoveRelayServiceTest의 검증 항목을 서버 판 계산 방식에 맞게 옮김)
// 기본 판: 0 출발 → 1, 2, 3 ... / 팀 0 말 = 0, 1 / 팀 1 말 = 100, 101
class GameServiceMoveTest {

    private static final String ROOM = "ROOM";
    private static final String HASH = "0123456789abcdef";

    private GameSession session;
    private RoomBroadcaster broadcaster;
    private CommandLog commandLog;
    private GameService service;

    @BeforeEach
    void setUp() {
        // 팀 0(p0)의 차례, 던지기는 끝났고 개(2칸) 결과를 가진 상태
        session = new GameSession(ROOM, new GameSetup(2, 2, "default", false), Map.of("p0", 0, "p1", 1),
                TestBoards.defaultBoard());
        session.setPhase(GamePhase.PLAYING);
        session.setCurrentTeam(0);
        session.getStoredResults().add(YutResult.Gae);

        GameSessionRepository repository = new GameSessionRepository();
        repository.save(session);
        broadcaster = mock(RoomBroadcaster.class);
        when(broadcaster.broadcast(any(), any(), any())).thenReturn(5L);
        commandLog = new CommandLog();

        service = new GameService(repository, new TurnManager(), mock(YutThrowService.class),
                mock(AugmentDraftService.class), mock(RethrowValidator.class), broadcaster,
                mock(GameProperties.class), mock(TurnTimerService.class), TestBoards.repository(), commandLog);
    }

    // 개(2칸)로 내 말(0번)을 2번 칸으로
    private MoveRequest validMove() {
        return new MoveRequest(0, 2, 2, HASH);
    }

    // 정상 이동: 판 적용 → MOVE_APPLIED 방송 → CommandLog 기록
    @Test
    void move() {
        service.move(ROOM, "p0", validMove());

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(broadcaster).broadcast(eq(ROOM), eq(MessageType.MOVE_APPLIED), captor.capture());
        MoveAppliedMessage applied = (MoveAppliedMessage) captor.getValue();
        assertThat(applied.team()).isZero();
        assertThat(applied.pieceId()).isZero();
        assertThat(applied.destinationNodeId()).isEqualTo(2);
        assertThat(applied.movedPieceIds()).containsExactly(0);
        assertThat(applied.capturedPieceIds()).isEmpty();
        assertThat(applied.stateHash()).isEqualTo(HASH);

        assertThat(commandLog.lastSeq(ROOM)).isEqualTo(5);
        assertThat(session.getBoardState().getPiece(0).getNode().getId()).isEqualTo(2);
    }

    // MOVE_APPLIED를 먼저 보내고 그다음 TURN_CHANGED (결과를 다 써서 다음 팀)
    @Test
    void order() {
        service.move(ROOM, "p0", validMove());

        InOrder inOrder = inOrder(broadcaster);
        inOrder.verify(broadcaster).broadcast(eq(ROOM), eq(MessageType.MOVE_APPLIED), any());
        inOrder.verify(broadcaster).broadcast(eq(ROOM), eq(MessageType.TURN_CHANGED), any());
        assertThat(session.getCurrentTeam()).isEqualTo(1);
    }

    // 잡으면 서버가 계산해서 한 번 더 던지게 하고 턴 유지
    @Test
    void captureKeepsTurn() {
        session.getBoardState().tryMove(100, 2, 2);   // 상대 말을 2번 칸에 미리 둔다

        service.move(ROOM, "p0", validMove());

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(broadcaster).broadcast(eq(ROOM), eq(MessageType.MOVE_APPLIED), captor.capture());
        assertThat(((MoveAppliedMessage) captor.getValue()).capturedPieceIds()).containsExactly(100);
        assertThat(session.getCurrentTeam()).isZero();
        assertThat(session.getRemainingThrows()).isEqualTo(1);
    }

    // 상대 차례면 NOT_YOUR_TURN, 아무것도 보내지 않는다
    @Test
    void notYourTurn() {
        assertThatThrownBy(() -> service.move(ROOM, "p1", new MoveRequest(100, 2, 2, HASH)))
                .isInstanceOf(ForbiddenException.class)
                .extracting("code").isEqualTo("NOT_YOUR_TURN");
        verifyNoInteractions(broadcaster);
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

    // 남은 던지기가 있으면 THROW_REMAINING
    @Test
    void throwRemaining() {
        session.setRemainingThrows(1);

        assertThatThrownBy(() -> service.move(ROOM, "p0", validMove()))
                .isInstanceOf(InvalidRequestException.class)
                .extracting("code").isEqualTo("THROW_REMAINING");
    }

    // 상대 팀 말이거나 없는 번호의 말이면 NOT_YOUR_PIECE
    @Test
    void notYourPiece() {
        assertThatThrownBy(() -> service.move(ROOM, "p0", new MoveRequest(100, 2, 2, HASH)))
                .isInstanceOf(ForbiddenException.class)
                .extracting("code").isEqualTo("NOT_YOUR_PIECE");
        assertThatThrownBy(() -> service.move(ROOM, "p0", new MoveRequest(5, 2, 2, HASH)))
                .isInstanceOf(ForbiddenException.class)
                .extracting("code").isEqualTo("NOT_YOUR_PIECE");   // 팀당 2개라 순번 5는 없음
    }

    // 가지고 있지 않은 윷 결과로 이동하면 INVALID_MOVE_RESULT, 아무것도 보내지 않는다
    @Test
    void invalidMoveCount() {
        assertThatThrownBy(() -> service.move(ROOM, "p0", new MoveRequest(0, 5, 5, HASH)))
                .isInstanceOf(InvalidRequestException.class)
                .extracting("code").isEqualTo("INVALID_MOVE_RESULT");
        verifyNoInteractions(broadcaster);
    }

    // 판 규칙상 갈 수 없는 칸이면 INVALID_MOVE, 말도 그대로
    @Test
    void invalidDestination() {
        assertThatThrownBy(() -> service.move(ROOM, "p0", new MoveRequest(0, 2, 7, HASH)))
                .isInstanceOf(InvalidRequestException.class)
                .extracting("code").isEqualTo("INVALID_MOVE");
        verifyNoInteractions(broadcaster);
        assertThat(session.getBoardState().getPiece(0).isWaiting()).isTrue();
        assertThat(session.getStoredResults()).isEqualTo(List.of(YutResult.Gae));
    }
}
