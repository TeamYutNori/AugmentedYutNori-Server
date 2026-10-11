package com.teamyutnori.yutnori.reward;

import com.teamyutnori.yutnori.ws.dto.WsMessages.RankingEntry;
import org.springframework.stereotype.Component;

// 게임 순위 → 보상 골드 계산 (Miro 임시 골드 명세서 기준)
//   금액 = 1등 금액 × 등수별 비율, 소수점은 반올림
//   1등 100% / 2등 80% / 3등 50% / 4등 20%  →  500 / 400 / 250 / 100
//   같은 등수는 같은 금액, 기권·끊김으로 나간 플레이어는 0
// rankings를 채우는 쪽(finishGame, 끊김 처리)은 entry(...)로 RankingEntry를 만들면 rewardGold가 같이 채워진다
@Component
public class RewardPolicy {

    public static final int DEFAULT_FIRST_PLACE_GOLD = 500;

    // 등수별 비율(%). 인덱스 0 = 1등. 이보다 낮은 등수는 0
    private static final int[] RATE_PERCENTS = {100, 80, 50, 20};

    private final int firstPlaceGold;

    public RewardPolicy() {
        this(DEFAULT_FIRST_PLACE_GOLD);
    }

    // 1등 금액을 바꿔 테스트하거나 조정할 때
    RewardPolicy(int firstPlaceGold) {
        if (firstPlaceGold < 0) throw new IllegalArgumentException("1등 금액은 0 이상이어야 합니다: " + firstPlaceGold);
        this.firstPlaceGold = firstPlaceGold;
    }

    public int firstPlaceGold() {
        return firstPlaceGold;
    }

    // 등수(1부터)로 받을 골드. 비율이 정해지지 않은 등수는 0
    public int goldFor(int rank) {
        if (rank < 1) throw new IllegalArgumentException("등수는 1부터입니다: " + rank);
        if (rank > RATE_PERCENTS.length) return 0;

        // 정수로만 계산해서 반올림: 금액 × 비율 / 100 (0.5 이상 올림)
        return (int) ((firstPlaceGold * (long) RATE_PERCENTS[rank - 1] + 50) / 100);
    }

    // 나간 플레이어는 등수와 상관없이 0
    public int goldFor(int rank, boolean leftGame) {
        return leftGame ? 0 : goldFor(rank);
    }

    // rankings 한 줄을 보상 금액까지 채워서 만든다
    public RankingEntry entry(String playerId, int team, int rank, boolean leftGame) {
        return new RankingEntry(playerId, team, rank, goldFor(rank, leftGame));
    }
}
