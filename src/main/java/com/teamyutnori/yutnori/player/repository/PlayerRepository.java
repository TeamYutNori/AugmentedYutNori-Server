package com.teamyutnori.yutnori.player.repository;

import com.teamyutnori.yutnori.player.entity.Player;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    // WebSocket 연결 시 게스트 토큰으로 플레이어 조회
    Optional<Player> findByGuestToken(String guestToken);

    // playerId로 플레이어 조회
    Optional<Player> findByPlayerId(String playerId);
}