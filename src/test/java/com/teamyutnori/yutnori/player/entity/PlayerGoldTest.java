package com.teamyutnori.yutnori.player.entity;

import com.teamyutnori.yutnori.common.ConflictException;
import com.teamyutnori.yutnori.common.InvalidRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlayerGoldTest {

    private Player newPlayer() {
        return Player.createFirebase("player-1", "uid-1", "test@example.com");
    }

    @Test
    void 새_플레이어는_골드_0() {
        assertThat(newPlayer().getGold()).isZero();
    }

    @Test
    void 골드_지급하면_늘어난다() {
        Player player = newPlayer();

        player.addGold(100);
        player.addGold(70);

        assertThat(player.getGold()).isEqualTo(170);
    }

    @Test
    void 골드_차감하면_줄어든다() {
        Player player = newPlayer();
        player.addGold(100);

        player.spendGold(30);

        assertThat(player.getGold()).isEqualTo(70);
    }

    @Test
    void 보유_골드를_전부_쓸_수_있다() {
        Player player = newPlayer();
        player.addGold(50);

        player.spendGold(50);

        assertThat(player.getGold()).isZero();
    }

    @Test
    void 골드가_부족하면_차감하지_않고_409() {
        Player player = newPlayer();
        player.addGold(20);

        assertThatThrownBy(() -> player.spendGold(30))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "NOT_ENOUGH_GOLD");
        assertThat(player.getGold()).isEqualTo(20);
    }

    @Test
    void 음수_수량은_거절() {
        Player player = newPlayer();

        assertThatThrownBy(() -> player.addGold(-1))
                .isInstanceOf(InvalidRequestException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_GOLD_AMOUNT");
        assertThatThrownBy(() -> player.spendGold(-1))
                .isInstanceOf(InvalidRequestException.class);
        assertThat(player.getGold()).isZero();
    }

    @Test
    void 지급이_int_범위를_넘으면_예외() {
        Player player = newPlayer();
        player.addGold(Integer.MAX_VALUE);

        assertThatThrownBy(() -> player.addGold(1)).isInstanceOf(ArithmeticException.class);
        assertThat(player.getGold()).isEqualTo(Integer.MAX_VALUE);
    }
}
