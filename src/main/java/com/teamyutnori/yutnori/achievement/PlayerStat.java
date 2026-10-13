package com.teamyutnori.yutnori.achievement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDate;

// 플레이어별 업적용 누적 횟수 (플레이어당 한 줄)
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "player_stats")
public class PlayerStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Player.playerId
    @Column(name = "player_id", nullable = false, unique = true, length = 36)
    private String playerId;

    @Column(nullable = false) @ColumnDefault("0")
    private int loginDays;

    // 마지막으로 접속 횟수를 올린 날 (서울 기준). 같은 날 다시 로그인하면 안 올린다
    @Column
    private LocalDate lastLoginDate;

    @Column(nullable = false) @ColumnDefault("0")
    private int gamesPlayed;

    @Column(nullable = false) @ColumnDefault("0")
    private int gachaDraws;

    @Column(nullable = false) @ColumnDefault("0")
    private int firstPlaces;

    public static PlayerStat of(String playerId) {
        PlayerStat stat = new PlayerStat();
        stat.playerId = playerId;
        return stat;
    }

    public int get(AchievementStat stat) {
        return switch (stat) {
            case LOGIN_DAYS -> loginDays;
            case GAMES_PLAYED -> gamesPlayed;
            case GACHA_DRAWS -> gachaDraws;
            case FIRST_PLACES -> firstPlaces;
        };
    }

    // 접속: 오늘 처음이면 +1 하고 true
    public boolean recordLogin(LocalDate today) {
        if (today.equals(lastLoginDate)) return false;
        lastLoginDate = today;
        loginDays = Math.addExact(loginDays, 1);
        return true;
    }

    // LOGIN_DAYS 외 횟수 +1 (접속은 recordLogin으로)
    public void increment(AchievementStat stat) {
        switch (stat) {
            case GAMES_PLAYED -> gamesPlayed = Math.addExact(gamesPlayed, 1);
            case GACHA_DRAWS -> gachaDraws = Math.addExact(gachaDraws, 1);
            case FIRST_PLACES -> firstPlaces = Math.addExact(firstPlaces, 1);
            case LOGIN_DAYS -> throw new IllegalArgumentException("접속 횟수는 recordLogin으로 올립니다.");
        }
    }
}
