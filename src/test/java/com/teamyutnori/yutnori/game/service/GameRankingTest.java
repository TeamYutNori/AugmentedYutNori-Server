package com.teamyutnori.yutnori.game.service;

import com.teamyutnori.yutnori.game.board.BoardState;
import com.teamyutnori.yutnori.game.board.TestBoards;
import com.teamyutnori.yutnori.game.model.GameSession;
import com.teamyutnori.yutnori.game.model.GameSetup;
import com.teamyutnori.yutnori.reward.RewardPolicy;
import com.teamyutnori.yutnori.ws.dto.WsMessages.RankingEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// 4팀, 팀당 말 2개. 보상 인원별 표: 4인 500/400/250/100, 3인 400/200/100, 2인 200/100
// 기준 턴은 기본값 1 (turnNumber 1 이하에 나가면 '기준 전')
class GameRankingTest {

    private final RewardPolicy policy = new RewardPolicy();
    private GameSession session;

    @BeforeEach
    void setUp() {
        session = new GameSession("ROOM01", new GameSetup(4, 2, "default", false),
                Map.of("p0", 0, "p1", 1, "p2", 2, "p3", 3), TestBoards.defaultBoard()).createPieces();
    }

    // 말 하나를 한 바퀴 돌려 완주시킨다 (4 → 9 → 14 → 19 → 도착)
    private void finishOne(int pieceId) {
        BoardState board = session.getBoardState();
        int[][] steps = {{4, 4}, {5, 9}, {5, 14}, {5, 19}, {2, 0}};
        for (int[] s : steps) {
            assertTrue(board.tryMove(pieceId, s[0], s[1]).isPresent(), "이동 실패: " + pieceId);
        }
    }

    private RankingEntry of(List<RankingEntry> rankings, int team) {
        return rankings.stream().filter(e -> e.team() == team).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("이긴 팀 1등, 나머지는 들어온 말 수 순, 같으면 같은 등수")
    void ranksByFinishedCount() {
        finishOne(0); finishOne(1);     // 팀 0: 2개 (승리)
        finishOne(200);                 // 팀 2: 1개
        finishOne(300);                 // 팀 3: 1개

        List<RankingEntry> r = GameRanking.build(session, 0, policy);

        assertEquals(4, r.size());
        assertEquals(1, of(r, 0).rank());
        assertEquals(2, of(r, 2).rank());
        assertEquals(2, of(r, 3).rank());
        assertEquals(4, of(r, 1).rank());          // 2등이 둘이라 3등은 건너뜀
        assertEquals(500, of(r, 0).rewardGold());
        assertEquals(400, of(r, 2).rewardGold());
        assertEquals(100, of(r, 1).rewardGold());
        assertEquals("p0", r.get(0).playerId());   // 등수 순으로 정렬
    }

    @Test
    @DisplayName("나간 팀은 남은 팀 다음 등수, 보상 0 (기준 뒤에 나가면 4인 표 그대로)")
    void removedTeamsAreLast() {
        session.setTurnNumber(5);
        session.markRemoved(1);
        session.markRemoved(3);
        session.getBoardState().removeTeam(1);
        session.getBoardState().removeTeam(3);
        finishOne(200);

        List<RankingEntry> r = GameRanking.build(session, 2, policy);

        assertEquals(1, of(r, 2).rank());
        assertEquals(2, of(r, 0).rank());
        assertEquals(3, of(r, 1).rank());
        assertEquals(3, of(r, 3).rank());
        assertEquals(0, of(r, 1).rewardGold());
        assertEquals(0, of(r, 3).rewardGold());
        assertEquals(400, of(r, 0).rewardGold());
    }

    @Test
    @DisplayName("1명만 남아 끝나면 남은 사람이 1등 (기준 뒤에 나감 → 4인 표)")
    void lastOneStanding() {
        session.setTurnNumber(5);
        session.markRemoved(0);
        session.markRemoved(1);
        session.markRemoved(2);

        List<RankingEntry> r = GameRanking.build(session, 3, policy);

        assertEquals(1, of(r, 3).rank());
        assertEquals(500, of(r, 3).rewardGold());
        assertEquals(2, of(r, 0).rank());
        assertEquals(0, of(r, 0).rewardGold());
    }

    @Test
    @DisplayName("4인에서 기준 턴 안에 1명 나가면 남은 3명은 3인 표 (400/200/100)")
    void earlyLeaveUsesSmallerTable() {
        session.setTurnNumber(1);   // 첫 턴 중에 나감
        session.markRemoved(1);
        session.getBoardState().removeTeam(1);
        session.setTurnNumber(9);
        finishOne(0); finishOne(1);   // 팀 0 승리
        finishOne(200);               // 팀 2: 1개, 팀 3: 0개

        List<RankingEntry> r = GameRanking.build(session, 0, policy);

        assertEquals(3, GameRanking.rewardPlayerCount(session, policy));
        assertEquals(400, of(r, 0).rewardGold());
        assertEquals(200, of(r, 2).rewardGold());
        assertEquals(100, of(r, 3).rewardGold());
        assertEquals(0, of(r, 1).rewardGold());
    }

    @Test
    @DisplayName("4인에서 기준 뒤에 1명 나가면 남은 3명은 4인 표 (500/400/250)")
    void lateLeaveKeepsStartingTable() {
        session.setTurnNumber(2);   // 첫 턴이 끝난 뒤
        session.markRemoved(1);
        session.getBoardState().removeTeam(1);
        finishOne(0); finishOne(1);
        finishOne(200);

        List<RankingEntry> r = GameRanking.build(session, 0, policy);

        assertEquals(4, GameRanking.rewardPlayerCount(session, policy));
        assertEquals(500, of(r, 0).rewardGold());
        assertEquals(400, of(r, 2).rewardGold());
        assertEquals(250, of(r, 3).rewardGold());
        assertEquals(0, of(r, 1).rewardGold());
    }

    @Test
    @DisplayName("증강 선택 중(turnNumber 0)에 나가도 기준 전으로 본다")
    void leaveDuringAugmentSelectIsEarly() {
        session.markRemoved(3);   // turnNumber 0

        assertEquals(3, GameRanking.rewardPlayerCount(session, policy));
    }

    @Test
    @DisplayName("2인에서 기준 전에 상대가 나가면 남은 사람도 0")
    void twoPlayerEarlyLeaveGivesNothing() {
        GameSession duo = new GameSession("ROOM02", new GameSetup(2, 2, "default", false),
                Map.of("a", 0, "b", 1), TestBoards.defaultBoard()).createPieces();
        duo.setTurnNumber(1);
        duo.markRemoved(1);

        List<RankingEntry> r = GameRanking.build(duo, 0, policy);

        assertEquals(1, GameRanking.rewardPlayerCount(duo, policy));
        assertEquals(0, r.stream().filter(e -> e.team() == 0).findFirst().orElseThrow().rewardGold());
    }

    @Test
    @DisplayName("기준 턴을 바꾸면 같은 상황도 보상 인원이 달라진다 (설정값)")
    void earlyLeaveTurnsIsConfigurable() {
        session.setTurnNumber(4);
        session.markRemoved(1);

        assertEquals(4, GameRanking.rewardPlayerCount(session, new RewardPolicy(1)));
        assertEquals(3, GameRanking.rewardPlayerCount(session, new RewardPolicy(5)));
    }
}
