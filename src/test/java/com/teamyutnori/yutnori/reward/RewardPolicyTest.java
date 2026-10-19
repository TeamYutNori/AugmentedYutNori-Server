package com.teamyutnori.yutnori.reward;

import com.teamyutnori.yutnori.ws.dto.WsMessages.RankingEntry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RewardPolicyTest {

    private final RewardPolicy policy = new RewardPolicy();

    @Test
    void 사인_표는_500_400_250_100() {
        assertThat(policy.goldFor(1, 4)).isEqualTo(500);
        assertThat(policy.goldFor(2, 4)).isEqualTo(400);
        assertThat(policy.goldFor(3, 4)).isEqualTo(250);
        assertThat(policy.goldFor(4, 4)).isEqualTo(100);
    }

    @Test
    void 삼인_표는_400_200_100() {
        assertThat(policy.goldFor(1, 3)).isEqualTo(400);
        assertThat(policy.goldFor(2, 3)).isEqualTo(200);
        assertThat(policy.goldFor(3, 3)).isEqualTo(100);
        assertThat(policy.goldFor(4, 3)).isZero();   // 3인 표에 4등 비율 없음
    }

    @Test
    void 이인_표는_200_100() {
        assertThat(policy.goldFor(1, 2)).isEqualTo(200);
        assertThat(policy.goldFor(2, 2)).isEqualTo(100);
        assertThat(policy.goldFor(3, 2)).isZero();
    }

    @Test
    void 보상_인원이_1_이하면_모두_0() {
        assertThat(policy.goldFor(1, 1)).isZero();
        assertThat(policy.goldFor(1, 0)).isZero();
    }

    @Test
    void 비율이_없는_등수는_0() {
        assertThat(policy.goldFor(5, 4)).isZero();
    }

    @Test
    void 나간_플레이어는_등수와_상관없이_0() {
        assertThat(policy.goldFor(1, 4, true)).isZero();
        assertThat(policy.goldFor(2, 4, true)).isZero();
        assertThat(policy.goldFor(2, 4, false)).isEqualTo(400);
    }

    @Test
    void 같은_등수는_같은_금액() {
        // 1, 2, 2, 4등
        assertThat(policy.entry("b", 1, 2, false, 4).rewardGold())
                .isEqualTo(policy.entry("c", 2, 2, false, 4).rewardGold());
    }

    @Test
    void 소수점은_반올림() {
        assertThat(RewardPolicy.percentOf(333, 80)).isEqualTo(266);   // 266.4
        assertThat(RewardPolicy.percentOf(333, 50)).isEqualTo(167);   // 166.5 → 올림
        assertThat(RewardPolicy.percentOf(333, 20)).isEqualTo(67);    // 66.6
    }

    @Test
    void 등수는_1부터() {
        assertThatThrownBy(() -> policy.goldFor(0, 4)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void entry는_보상까지_채운다() {
        RankingEntry entry = policy.entry("player-1", 2, 3, false, 4);

        assertThat(entry.playerId()).isEqualTo("player-1");
        assertThat(entry.team()).isEqualTo(2);
        assertThat(entry.rank()).isEqualTo(3);
        assertThat(entry.rewardGold()).isEqualTo(250);
    }

    @Test
    void 나간_플레이어_entry는_0골드이고_leftGame() {
        RankingEntry left = policy.entry("left", 3, 4, true, 4);

        assertThat(left.rewardGold()).isZero();
        assertThat(left.leftGame()).isTrue();
    }

    @Test
    void 남았지만_보상_인원이_1이면_0골드여도_leftGame은_false() {
        RankingEntry stayed = policy.entry("stayed", 0, 1, false, 1);

        assertThat(stayed.rewardGold()).isZero();
        assertThat(stayed.leftGame()).isFalse();
    }

    @Test
    void 기준_라운드_기본값은_1이고_첫_바퀴까지가_기준_전() {
        assertThat(policy.earlyLeaveRounds()).isEqualTo(1);
        assertThat(policy.isEarlyLeave(0)).isTrue();    // 증강 선택 중
        assertThat(policy.isEarlyLeave(1)).isTrue();    // 첫 바퀴
        assertThat(policy.isEarlyLeave(2)).isFalse();   // 두 번째 바퀴
    }

    @Test
    void 기준_라운드는_바꿀_수_있고_음수는_안_된다() {
        assertThat(new RewardPolicy(5).isEarlyLeave(5)).isTrue();
        assertThat(new RewardPolicy(5).isEarlyLeave(6)).isFalse();
        assertThatThrownBy(() -> new RewardPolicy(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
