package com.teamyutnori.yutnori.achievement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

// 달성한 업적 한 개 (플레이어 + 업적 id로 한 줄). 보상을 받으면 claimedAt이 채워진다
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "player_achievements",
        uniqueConstraints = @UniqueConstraint(columnNames = {"player_id", "achievement_id"})
)
public class PlayerAchievement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "player_id", nullable = false, length = 36)
    private String playerId;

    // achievements.json의 id
    @Column(name = "achievement_id", nullable = false, length = 64)
    private String achievementId;

    @Column(nullable = false)
    private Instant achievedAt;

    // 보상 받은 시각. 아직 안 받았으면 null
    @Column
    private Instant claimedAt;

    // 수령 버튼을 동시에 두 번 눌러도 한 번만 지급되도록 (늦은 쪽은 저장 시 실패)
    @Version
    private Long version;

    public static PlayerAchievement achieved(String playerId, String achievementId, Instant now) {
        PlayerAchievement achievement = new PlayerAchievement();
        achievement.playerId = playerId;
        achievement.achievementId = achievementId;
        achievement.achievedAt = now;
        return achievement;
    }

    public boolean isClaimed() {
        return claimedAt != null;
    }

    public void claim(Instant now) {
        this.claimedAt = now;
    }
}
