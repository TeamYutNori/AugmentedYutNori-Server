package com.teamyutnori.yutnori.game.service;

import com.teamyutnori.yutnori.game.board.BoardState;
import com.teamyutnori.yutnori.game.model.GameSession;
import com.teamyutnori.yutnori.reward.RewardPolicy;
import com.teamyutnori.yutnori.ws.dto.WsMessages.RankingEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;

// 게임 종료 시 순위표(rankings) 만들기
//  - 남아 있는 팀: 들어온 말 수가 많은 순. 같으면 같은 등수, 다음 등수는 건너뜀 (1, 1, 3)
//  - 이긴 팀(winnerTeam)은 항상 1등
//  - 나간 팀: 남은 팀 다음 등수로 모두 같은 꼴찌, leftGame = true → 보상 0
// 보상 금액은 RewardPolicy.entry가 등수와 보상 인원으로 채운다
//  - 보상 인원 = 시작 인원 − 기준 턴(RewardPolicy.earlyLeaveTurns) 안에 나간 팀 수 → 이 인원의 표로 보상
//    예) 4인에서 첫 턴에 1명 나감 → 남은 3명은 3인 표 / 기준 뒤에 나감 → 4인 표 / 2인에서 상대가 일찍 나감 → 0
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

        int playerCount = rewardPlayerCount(session, rewardPolicy);
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
            add(rankings, session, team, rank, false, playerCount, rewardPolicy);
        }

        int leftRank = active.size() + 1;
        for (int t = 0; t < session.getSetup().teamCount(); t++) {
            if (session.isRemoved(t)) add(rankings, session, t, leftRank, true, playerCount, rewardPolicy);
        }
        return rankings;
    }

    // 보상 인원: 시작 인원에서 기준 턴 안에(증강 선택 중 포함) 나간 팀을 뺀 수
    static int rewardPlayerCount(GameSession session, RewardPolicy rewardPolicy) {
        int count = session.getSetup().teamCount();
        for (int t = 0; t < session.getSetup().teamCount(); t++) {
            OptionalInt removedAt = session.removedAtTurn(t);
            if (removedAt.isPresent() && rewardPolicy.isEarlyLeave(removedAt.getAsInt())) count--;
        }
        return count;
    }

    private static void add(List<RankingEntry> rankings, GameSession session, int team, int rank,
                            boolean leftGame, int playerCount, RewardPolicy rewardPolicy) {
        session.playerOf(team).ifPresent(playerId ->
                rankings.add(rewardPolicy.entry(playerId, team, rank, leftGame, playerCount)));
    }
}
