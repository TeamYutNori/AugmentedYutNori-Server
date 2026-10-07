package com.teamyutnori.yutnori.game.service;

import com.teamyutnori.yutnori.common.InvalidRequestException;
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
        session = new GameSession("ROOM", new GameSetup(2, 2, "default", false), Map.of("p0", 0, "p1", 1));
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

    @Test
    @DisplayName("마지막 팀 다음은 팀 0")
    void wrapsAroundTeams() {
        turnManager.nextTurn(session);
        turnManager.nextTurn(session);
        assertEquals(0, session.getCurrentTeam());
        assertEquals(3, session.getTurnNumber());
    }
}
