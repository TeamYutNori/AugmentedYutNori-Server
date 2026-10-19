package com.teamyutnori.yutnori.reward;

import com.teamyutnori.yutnori.ws.dto.WsMessages.RankingEntry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

// 게임 순위 → 보상 골드 계산 (골드 명세서 기준)
//   보상 인원별로 1등 금액과 등수별 비율이 다르다. 금액 = 1등 금액 × 비율, 소수점은 반올림
//     4인: 500 × 100/80/50/20%  →  500 / 400 / 250 / 100
//     3인: 400 × 100/50/25%     →  400 / 200 / 100
//     2인: 200 × 100/50%        →  200 / 100
//   보상 인원 = 시작 인원 − 기준 라운드(earlyLeaveRounds) 안에 나간 사람 수. 계산은 GameRanking.build
//   라운드 = 남은 플레이어가 모두 한 번씩 차례를 도는 것 (GameSession.roundNumber, 첫 턴 = 1, 증강 선택 중 = 0)
//   보상 인원이 1 이하면 남은 사람도 0 (2인전에서 상대가 일찍 나간 경우 등, 파밍 방지)
//   같은 등수는 같은 금액, 기권·끊김으로 나간 플레이어는 0
// rankings를 채우는 쪽은 entry(...)로 RankingEntry를 만들면 rewardGold가 같이 채워진다
@Component
public class RewardPolicy {

    // 기준 라운드 기본값. 이 라운드가 끝나기 전에(증강 선택 중 포함) 나간 사람은 보상 인원에서 뺀다
    // 지금은 테스트용으로 1 (첫 바퀴가 끝나기 전). application.properties의 reward.early-leave-rounds로 바꾼다
    public static final int DEFAULT_EARLY_LEAVE_ROUNDS = 1;

    // 보상 인원 → 1등 금액과 등수별 비율(%). 비율 인덱스 0 = 1등. 비율이 없는 등수는 0
    private static final Map<Integer, Table> TABLES = Map.of(
            4, new Table(500, new int[]{100, 80, 50, 20}),
            3, new Table(400, new int[]{100, 50, 25}),
            2, new Table(200, new int[]{100, 50})
    );
    private static final int MAX_TABLE_PLAYERS = 4;

    private final int earlyLeaveRounds;

    public RewardPolicy() {
        this(DEFAULT_EARLY_LEAVE_ROUNDS);
    }

    @Autowired
    public RewardPolicy(@Value("${reward.early-leave-rounds:" + DEFAULT_EARLY_LEAVE_ROUNDS + "}") int earlyLeaveRounds) {
        if (earlyLeaveRounds < 0) throw new IllegalArgumentException("기준 라운드는 0 이상이어야 합니다: " + earlyLeaveRounds);
        this.earlyLeaveRounds = earlyLeaveRounds;
    }

    public int earlyLeaveRounds() {
        return earlyLeaveRounds;
    }

    // 나간 순간의 라운드 번호(GameSession.roundNumber, 증강 선택 중은 0)가 기준 라운드 이하면 '기준 전'에 나간 것
    //  예) 기준 1: 증강 선택 중이나 첫 바퀴에 나가면 기준 전, 두 번째 바퀴부터는 기준 뒤
    public boolean isEarlyLeave(int removedAtRound) {
        return removedAtRound <= earlyLeaveRounds;
    }

    // 등수(1부터)와 보상 인원으로 받을 골드. 보상 인원 1 이하 또는 비율이 없는 등수는 0
    public int goldFor(int rank, int playerCount) {
        if (rank < 1) throw new IllegalArgumentException("등수는 1부터입니다: " + rank);
        if (playerCount <= 1) return 0;

        Table table = TABLES.get(Math.min(playerCount, MAX_TABLE_PLAYERS));
        if (rank > table.ratePercents().length) return 0;
        return percentOf(table.firstPlaceGold(), table.ratePercents()[rank - 1]);
    }

    // 나간 플레이어는 등수와 상관없이 0
    public int goldFor(int rank, int playerCount, boolean leftGame) {
        return leftGame ? 0 : goldFor(rank, playerCount);
    }

    // rankings 한 줄을 보상 금액까지 채워서 만든다
    public RankingEntry entry(String playerId, int team, int rank, boolean leftGame, int playerCount) {
        return new RankingEntry(playerId, team, rank, goldFor(rank, playerCount, leftGame), leftGame);
    }

    // 정수로만 계산해서 반올림: 금액 × 비율 / 100 (0.5 이상 올림)
    static int percentOf(int gold, int percent) {
        return (int) ((gold * (long) percent + 50) / 100);
    }

    private record Table(int firstPlaceGold, int[] ratePercents) {}
}
