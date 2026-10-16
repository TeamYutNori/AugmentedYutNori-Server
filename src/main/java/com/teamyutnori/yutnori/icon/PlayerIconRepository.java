package com.teamyutnori.yutnori.icon;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlayerIconRepository extends JpaRepository<PlayerIcon, Long> {

    // 이 플레이어가 이 아이콘을 가지고 있는지 (중복 뽑기 확인)
    Optional<PlayerIcon> findByPlayerIdAndIconId(String playerId, String iconId);

    // 이 플레이어가 가진 아이콘 전부 (도감)
    List<PlayerIcon> findAllByPlayerId(String playerId);

    // 게스트 정리 (GuestCleanupService)
    void deleteByPlayerId(String playerId);
}
