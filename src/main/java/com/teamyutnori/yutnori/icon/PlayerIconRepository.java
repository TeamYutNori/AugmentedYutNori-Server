package com.teamyutnori.yutnori.icon;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlayerIconRepository extends JpaRepository<PlayerIcon, Long> {

    // 이 플레이어가 이 아이콘을 가지고 있는지 (중복 뽑기 확인)
    Optional<PlayerIcon> findByPlayerIdAndIconId(String playerId, String iconId);
}
