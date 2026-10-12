package com.teamyutnori.yutnori.game.service;

import com.teamyutnori.yutnori.game.board.BoardState;
import com.teamyutnori.yutnori.game.model.GameSession;
import com.teamyutnori.yutnori.reward.RewardPolicy;
import com.teamyutnori.yutnori.ws.dto.WsMessages.RankingEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// 게임 종료 시 순위표(rankings) 만들기
//  - 남아 있는 팀: 들어온 말 수가 많은 순. 같으면 같은 등수, 다음 등수는 건너뜀 (1, 1, 3)
//  - 이긴 팀(winnerTeam)은 항상 1등
//  - 나간 팀: 남은 팀 다음 등수로 모두 같은 꼴찌, leftGame = true → 보상 0
// 보상 금액은 RewardPolicy.entry가 등수로 채운다
public final class GameRanking {

    private GameRanking() {}

    public static List<RankingEntry> build(GameSession session, int winnerTeam, RewardPolicy rewardPolicy) {
        BoardState board = session.getBoardState();
        List<Integer> active = new ArrayList<>(session.activeTeams());

        // 이긴 팀 먼저, 그다음 들어온 말 수 내림차순, 같으면 팀 번호 순
        active.sort(Comparator
                .comparing((Integer t) -> t != winnerTeam)
                .thenComparing(t -> -board.finishedCount(t))
                .thenComparing(t -> t));

        List<RankingEntry> rankings = new ArrayList<>();
        int rank = 0;
        int prevCount = -1;
        for (int i = 0; i < active.size(); i++) {
            int team = active.get(i);
            int count = board.finishedCount(team);
            // 이긴 팀은 단독 1등. 나머지는 앞 팀과 들어온 수가 같으면 같은 등수
            boolean tie = i > 0 && active.get(i - 1) != winnerTeam && count == prevCount;
            if (!tie) rank = i + 1;
            prevCount = count;
            add(rankings, session, team, rank, false, rewardPolicy);
        }

        int leftRank = active.size() + 1;
        for (int t = 0; t < session.getSetup().teamCount(); t++) {
            if (session.isRemoved(t)) add(rankings, session, t, leftRank, true, rewardPolicy);
        }
        return rankings;
    }

    private static void add(List<RankingEntry> rankings, GameSession session, int team, int rank,
                            boolean leftGame, RewardPolicy rewardPolicy) {
        session.playerOf(team).ifPresent(playerId ->
                rankings.add(rewardPolicy.entry(playerId, team, rank, leftGame)));
    }
}
