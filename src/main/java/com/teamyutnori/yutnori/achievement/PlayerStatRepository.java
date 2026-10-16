package com.teamyutnori.yutnori.achievement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlayerStatRepository extends JpaRepository<PlayerStat, Long> {

    Optional<PlayerStat> findByPlayerId(String playerId);

    // 게스트 정리 (GuestCleanupService)
    void deleteByPlayerId(String playerId);
}
