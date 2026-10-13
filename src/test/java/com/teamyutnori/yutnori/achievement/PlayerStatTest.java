package com.teamyutnori.yutnori.achievement;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class PlayerStatTest {

    private static final LocalDate DAY1 = LocalDate.of(2026, 10, 13);

    @Test
    void 같은_날_여러_번_접속해도_한_번만_센다() {
        PlayerStat stat = PlayerStat.of("p1");

        assertTrue(stat.recordLogin(DAY1));
        assertFalse(stat.recordLogin(DAY1));
        assertTrue(stat.recordLogin(DAY1.plusDays(1)));

        assertEquals(2, stat.get(AchievementStat.LOGIN_DAYS));
    }

    @Test
    void 종류별로_따로_센다() {
        PlayerStat stat = PlayerStat.of("p1");

        stat.increment(AchievementStat.GAMES_PLAYED);
        stat.increment(AchievementStat.GAMES_PLAYED);
        stat.increment(AchievementStat.GACHA_DRAWS);
        stat.increment(AchievementStat.FIRST_PLACES);

        assertEquals(2, stat.get(AchievementStat.GAMES_PLAYED));
        assertEquals(1, stat.get(AchievementStat.GACHA_DRAWS));
        assertEquals(1, stat.get(AchievementStat.FIRST_PLACES));
        assertEquals(0, stat.get(AchievementStat.LOGIN_DAYS));
    }

    @Test
    void 접속은_increment로_올릴_수_없다() {
        assertThrows(IllegalArgumentException.class, () -> PlayerStat.of("p1").increment(AchievementStat.LOGIN_DAYS));
    }
}
