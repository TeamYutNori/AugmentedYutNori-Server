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

// 4팀, 팀당 말 2개. 보상: 1등 500 / 2등 400 / 3등 250 / 4등 100
class GameRankingTest {

    private final RewardPolicy policy = new RewardPolicy();
    private GameSession session;

    @BeforeEach
    void setUp() {
        session = new GameSession("ROOM01", new GameSetup(4, 2, "default", false),
                Map.of("p0", 0, "p1", 1, "p2", 2, "p3", 3), TestBoards.defaultBoard());
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
    @DisplayName("나간 팀은 남은 팀 다음 등수, 보상 0")
    void removedTeamsAreLast() {
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
    @DisplayName("1명만 남아 끝나면 남은 사람이 1등")
    void lastOneStanding() {
        session.markRemoved(0);
        session.markRemoved(1);
        session.markRemoved(2);

        List<RankingEntry> r = GameRanking.build(session, 3, policy);

        assertEquals(1, of(r, 3).rank());
        assertEquals(500, of(r, 3).rewardGold());
        assertEquals(2, of(r, 0).rank());
        assertEquals(0, of(r, 0).rewardGold());
    }
}
