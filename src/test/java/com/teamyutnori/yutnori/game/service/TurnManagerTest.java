package com.teamyutnori.yutnori.game.service;

import com.teamyutnori.yutnori.common.InvalidRequestException;
import com.teamyutnori.yutnori.game.augment.AugmentIds;
import com.teamyutnori.yutnori.game.board.MoveResult;
import com.teamyutnori.yutnori.game.board.TestBoards;
import com.teamyutnori.yutnori.game.model.GameSession;
import com.teamyutnori.yutnori.game.model.GameSetup;
import com.teamyutnori.yutnori.game.yut.YutResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// Unity TurnManager + GameRules 턴 규칙과 같은지 확인
class TurnManagerTest {

    private final TurnManager turnManager = new TurnManager();
    private GameSession session;

    @BeforeEach
    void setUp() {
        session = new GameSession("ROOM", new GameSetup(2, 2, "default", false), Map.of("p0", 0, "p1", 1), TestBoards.defaultBoard()).createPieces();
        turnManager.startFirstTurn(session);
    }

    @Test
    @DisplayName("첫 턴: 팀 0, 던지기 1회, 턴 번호 1")
    void firstTurn() {
        assertEquals(0, session.getCurrentTeam());
        assertEquals(1, session.getRemainingThrows());
        assertEquals(1, session.getTurnNumber());
    }

    @Test
    @DisplayName("윷·모는 한 번 더, 개는 던지기 소모")
    void extraThrowOnYutMo() {
        turnManager.applyThrow(session, YutResult.Mo);
        assertEquals(1, session.getRemainingThrows());
        turnManager.applyThrow(session, YutResult.Gae);
        assertEquals(0, session.getRemainingThrows());
        assertEquals(List.of(YutResult.Mo, YutResult.Gae), session.getStoredResults());
        assertEquals(YutResult.Gae, session.getLatestResult());
    }

    @Test
    @DisplayName("결과가 남아 있으면 턴 유지, 다 쓰면 다음 팀으로")
    void turnEndsWhenNothingLeft() {
        turnManager.applyThrow(session, YutResult.Mo);
        turnManager.applyThrow(session, YutResult.Gae);

        assertFalse(turnManager.afterMove(session, 5, 0, true));   // 모로 이동, 개 남음
        assertTrue(turnManager.afterMove(session, 2, 0, false));   // 개로 이동, 끝

        assertEquals(1, session.getCurrentTeam());
        assertEquals(1, session.getRemainingThrows());
        assertTrue(session.getStoredResults().isEmpty());
        assertFalse(session.isMovedThisTurn());
        assertEquals(2, session.getTurnNumber());
    }

    @Test
    @DisplayName("잡으면 한 번 더 던질 수 있어 턴 유지")
    void captureGrantsExtraThrow() {
        turnManager.applyThrow(session, YutResult.Do);
        assertFalse(turnManager.afterMove(session, 1, 1, false));
        assertEquals(1, session.getRemainingThrows());
        assertTrue(session.isMovedThisTurn());
    }

    @Test
    @DisplayName("재던지기: 마지막 결과 교체 + 윷·모 보너스 재계산")
    void rethrowReplacesLastResult() {
        turnManager.applyThrow(session, YutResult.Geol);
        turnManager.applyRethrow(session, YutResult.Yut);
        assertEquals(List.of(YutResult.Yut), session.getStoredResults());
        assertEquals(1, session.getRemainingThrows());   // 윷으로 바뀌어 한 번 더
    }

    @Test
    @DisplayName("저장된 적 없는 결과로 이동했다는 보고는 거부")
    void rejectUnknownMoveCount() {
        turnManager.applyThrow(session, YutResult.Gae);
        assertThrows(InvalidRequestException.class, () -> turnManager.afterMove(session, 3, 0, false));
    }

    // ── 판(BoardState)으로 계산하는 턴 처리 ──
    // 기본 판: 0 출발 / 1~4 바깥길. 팀 0 말 = 0, 1 / 팀 1 말 = 100, 101

    @Test
    @DisplayName("판에 말이 없는데 빽도만 나오면 바로 다음 팀")
    void backDoWithNoPieceOnBoardEndsTurn() {
        turnManager.applyThrow(session, YutResult.BackDo);
        assertTrue(turnManager.endTurnIfNoAction(session));
        assertEquals(1, session.getCurrentTeam());
    }

    @Test
    @DisplayName("움직일 수 있는 결과가 있으면 던진 뒤에도 턴 유지")
    void keepTurnWhenMovable() {
        turnManager.applyThrow(session, YutResult.Gae);
        assertFalse(turnManager.endTurnIfNoAction(session));
        assertEquals(0, session.getCurrentTeam());
    }

    @Test
    @DisplayName("서버가 계산한 이동 결과: 잡으면 한 번 더 던질 수 있어 턴 유지")
    void afterMoveWithCapture() {
        session.getBoardState().tryMove(100, 2, 2);   // 상대 말을 2번 칸에 미리 둔다
        turnManager.applyThrow(session, YutResult.Gae);

        MoveResult result = session.getBoardState().tryMove(0, 2, 2).orElseThrow();
        assertEquals(1, result.capturedCount());
        assertFalse(turnManager.afterMove(session, result));
        assertEquals(1, session.getRemainingThrows());
        assertTrue(session.getStoredResults().isEmpty());
    }

    @Test
    @DisplayName("서버가 계산한 이동 결과: 결과를 다 쓰면 다음 팀")
    void afterMoveEndsTurn() {
        turnManager.applyThrow(session, YutResult.Gae);
        MoveResult result = session.getBoardState().tryMove(0, 2, 2).orElseThrow();
        assertTrue(turnManager.afterMove(session, result));
        assertEquals(1, session.getCurrentTeam());
    }

    @Test
    @DisplayName("남은 결과가 빽도뿐이어도 판 위 말이 있으면 턴 유지")
    void remainingBackDoWithPieceOnBoard() {
        turnManager.applyThrow(session, YutResult.Mo);       // 한 번 더
        turnManager.applyThrow(session, YutResult.BackDo);   // 던지기 끝
        MoveResult result = session.getBoardState().tryMove(0, 5, 5).orElseThrow();
        assertFalse(turnManager.afterMove(session, result), "5번 칸 말이 빽도로 움직일 수 있음");
        assertEquals(List.of(YutResult.BackDo), session.getStoredResults());
    }

    // ── 던지기·턴 규칙 증강 ──

    @Test
    @DisplayName("사냥꾼: 윷·모가 나와도 한 번 더 없음")
    void hunterNoExtraOnYutMo() {
        session.addAugment(0, AugmentIds.HUNTER);
        turnManager.applyThrow(session, YutResult.Mo);
        assertEquals(0, session.getRemainingThrows());
    }

    @Test
    @DisplayName("사냥꾼: 잡으면 2번 더 던진다")
    void hunterCaptureBonus() {
        session.addAugment(0, AugmentIds.HUNTER);
        turnManager.applyThrow(session, YutResult.Do);
        assertFalse(turnManager.afterMove(session, 1, 1, false));
        assertEquals(2, session.getRemainingThrows());
    }

    @Test
    @DisplayName("도박사: 도는 모로 저장되지만 한 번 더는 없다")
    void gamblerDoToMo() {
        session.addAugment(0, AugmentIds.GAMBLER);
        turnManager.applyThrow(session, YutResult.Do);
        assertEquals(List.of(YutResult.Mo), session.getStoredResults());
        assertEquals(YutResult.Mo, session.getLatestResult());
        assertEquals(0, session.getRemainingThrows());
    }

    @Test
    @DisplayName("도박사: 모는 도로 저장, 윷은 그대로 한 번 더")
    void gamblerMoToDoAndYutUnchanged() {
        session.addAugment(0, AugmentIds.GAMBLER);
        turnManager.applyThrow(session, YutResult.Yut);
        assertEquals(1, session.getRemainingThrows());
        turnManager.applyThrow(session, YutResult.Mo);
        assertEquals(List.of(YutResult.Yut, YutResult.Do), session.getStoredResults());
        assertEquals(0, session.getRemainingThrows());
    }

    @Test
    @DisplayName("증강은 현재 차례 팀 것만 적용")
    void augmentOnlyForCurrentTeam() {
        session.addAugment(1, AugmentIds.GAMBLER);
        turnManager.applyThrow(session, YutResult.Do);
        assertEquals(List.of(YutResult.Do), session.getStoredResults());
    }

    @Test
    @DisplayName("다시 던지기: 턴 사용 표시, 턴이 바뀌면 초기화")
    void rethrowFlagResetsOnNewTurn() {
        turnManager.applyThrow(session, YutResult.Gae);
        turnManager.applyRethrow(session, YutResult.Do);
        assertTrue(session.isRethrownThisTurn());
        turnManager.nextTurn(session);
        assertFalse(session.isRethrownThisTurn());
    }

    @Test
    @DisplayName("나간 팀은 차례를 건너뛴다")
    void skipsRemovedTeam() {
        GameSession three = new GameSession("ROOM3", new GameSetup(3, 2, "default", false),
                Map.of("p0", 0, "p1", 1, "p2", 2), TestBoards.defaultBoard()).createPieces();
        turnManager.startFirstTurn(three);
        three.markRemoved(1);
        turnManager.nextTurn(three);
        assertEquals(2, three.getCurrentTeam());
        turnManager.nextTurn(three);
        assertEquals(0, three.getCurrentTeam());
    }

    @Test
    @DisplayName("팀 0이 증강 선택 중에 나가면 첫 턴은 팀 1")
    void firstTurnSkipsRemovedTeam() {
        GameSession fresh = new GameSession("ROOM2", new GameSetup(2, 2, "default", false),
                Map.of("p0", 0, "p1", 1), TestBoards.defaultBoard()).createPieces();
        fresh.markRemoved(0);
        turnManager.startFirstTurn(fresh);
        assertEquals(1, fresh.getCurrentTeam());
    }

    @Test
    @DisplayName("마지막 팀 다음은 팀 0")
    void wrapsAroundTeams() {
        turnManager.nextTurn(session);
        turnManager.nextTurn(session);
        assertEquals(0, session.getCurrentTeam());
        assertEquals(3, session.getTurnNumber());
    }

    @Test
    @DisplayName("라운드: 첫 턴 1, 모두 한 번씩 하고 앞 팀으로 돌아오면 +1")
    void roundAdvancesWhenOrderWraps() {
        assertEquals(1, session.getRoundNumber());
        turnManager.nextTurn(session);   // 팀 1
        assertEquals(1, session.getRoundNumber());
        turnManager.nextTurn(session);   // 다시 팀 0
        assertEquals(2, session.getRoundNumber());
    }

    @Test
    @DisplayName("라운드: 사람이 나가면 한 바퀴가 짧아진다 (4인에서 2명 나가면 2턴이 한 바퀴)")
    void roundShrinksWhenTeamsLeave() {
        GameSession four = new GameSession("ROOM4", new GameSetup(4, 2, "default", false),
                Map.of("a", 0, "b", 1, "c", 2, "d", 3), TestBoards.defaultBoard()).createPieces();
        turnManager.startFirstTurn(four);       // 팀 0, 1라운드
        four.markRemoved(1);
        four.markRemoved(2);
        turnManager.nextTurn(four);             // 팀 3, 1라운드
        assertEquals(3, four.getCurrentTeam());
        assertEquals(1, four.getRoundNumber());
        turnManager.nextTurn(four);             // 팀 0, 2라운드 (턴 번호는 3)
        assertEquals(0, four.getCurrentTeam());
        assertEquals(2, four.getRoundNumber());
        assertEquals(3, four.getTurnNumber());
    }
}
