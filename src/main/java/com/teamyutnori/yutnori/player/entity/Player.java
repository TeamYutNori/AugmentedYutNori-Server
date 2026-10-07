package com.teamyutnori.yutnori.player.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "players")
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 서버/클라이언트에서 사용하는 플레이어 식별자
    @Column(nullable = false, unique = true, length = 36)
    private String playerId;

    // 플레이어 닉네임
    @Column(nullable = false, length = 20)
    private String nickname;

    // WebSocket 연결 시 플레이어 인증에 사용하는 게스트 토큰
    @Column(nullable = false, unique = true, length = 36)
    private String guestToken;

    // 플레이어 생성 시 필요한 값 초기화
    public Player(String playerId, String nickname, String guestToken) {
        this.playerId = playerId;
        this.nickname = nickname;
        this.guestToken = guestToken;
    }
}