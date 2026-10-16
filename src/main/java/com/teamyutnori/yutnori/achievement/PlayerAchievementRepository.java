package com.teamyutnori.yutnori.achievement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlayerAchievementRepository extends JpaRepository<PlayerAchievement, Long> {

    Optional<PlayerAchievement> findByPlayerIdAndAchievementId(String playerId, String achievementId);

    List<PlayerAchievement> findAllByPlayerId(String playerId);

    // 게스트 정리 (GuestCleanupService)
    void deleteByPlayerId(String playerId);
}
