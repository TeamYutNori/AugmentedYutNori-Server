package com.teamyutnori.yutnori.reward;

import com.teamyutnori.yutnori.ws.dto.WsMessages.RankingEntry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RewardPolicyTest {

    private final RewardPolicy policy = new RewardPolicy();

    @Test
    void 기본_금액은_500_400_250_100() {
        assertThat(policy.goldFor(1)).isEqualTo(500);
        assertThat(policy.goldFor(2)).isEqualTo(400);
        assertThat(policy.goldFor(3)).isEqualTo(250);
        assertThat(policy.goldFor(4)).isEqualTo(100);
    }

    @Test
    void 비율이_없는_등수는_0() {
        assertThat(policy.goldFor(5)).isZero();
    }

    @Test
    void 나간_플레이어는_등수와_상관없이_0() {
        assertThat(policy.goldFor(1, true)).isZero();
        assertThat(policy.goldFor(2, true)).isZero();
        assertThat(policy.goldFor(2, false)).isEqualTo(400);
    }

    @Test
    void 같은_등수는_같은_금액() {
        // 1, 2, 2, 4등
        assertThat(policy.goldFor(2)).isEqualTo(policy.goldFor(2));
        assertThat(policy.entry("b", 1, 2, false).rewardGold())
                .isEqualTo(policy.entry("c", 2, 2, false).rewardGold());
    }

    @Test
    void 소수점은_반올림() {
        RewardPolicy custom = new RewardPolicy(333);

        assertThat(custom.goldFor(1)).isEqualTo(333);
        assertThat(custom.goldFor(2)).isEqualTo(266);   // 266.4
        assertThat(custom.goldFor(3)).isEqualTo(167);   // 166.5 → 올림
        assertThat(custom.goldFor(4)).isEqualTo(67);    // 66.6
    }

    @Test
    void 등수는_1부터() {
        assertThatThrownBy(() -> policy.goldFor(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void entry는_보상까지_채운다() {
        RankingEntry entry = policy.entry("player-1", 2, 3, false);

        assertThat(entry.playerId()).isEqualTo("player-1");
        assertThat(entry.team()).isEqualTo(2);
        assertThat(entry.rank()).isEqualTo(3);
        assertThat(entry.rewardGold()).isEqualTo(250);
    }

    @Test
    void 나간_플레이어_entry는_0골드() {
        assertThat(policy.entry("left", 3, 4, true).rewardGold()).isZero();
    }
}
