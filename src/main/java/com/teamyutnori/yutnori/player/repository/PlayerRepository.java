package com.teamyutnori.yutnori.player.repository;

import com.teamyutnori.yutnori.player.entity.Player;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    // Firebase UID로 기존 Player 조회
    Optional<Player> findByFirebaseUid(String firebaseUid);

    // 게임 내부 playerId로 Player 조회
    Optional<Player> findByPlayerId(String playerId);

    // 게스트 토큰으로 Player 조회
    Optional<Player> findByGuestToken(String guestToken);

    // 닉네임 중복 확인
    boolean existsByNickname(String nickname);
}