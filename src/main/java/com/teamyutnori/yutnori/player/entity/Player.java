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

    // DB에서 Player 데이터를 구분하기 위한 번호
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 게임 내부에서 플레이어를 구분하기 위한 고유 ID
    @Column(nullable = false, unique = true, length = 36)
    private String playerId;

    // Google 로그인 사용자를 Player와 연결하기 위한 Firebase UID
    // 게스트 플레이어는 값이 없음
    @Column(unique = true)
    private String firebaseUid;

    // Google 로그인에 사용한 이메일
    // 게스트 플레이어는 값이 없음
    @Column
    private String email;

    // 게임에서 사용하는 닉네임
    @Column(unique = true, length = 20)
    private String nickname;

    // 에디터 테스트용 게스트 로그인 토큰
    // Firebase 로그인 사용자는 값이 없음
    @Column(unique = true, length = 36)
    private String guestToken;

    // 게스트 플레이어 생성
    public static Player createGuest(
            String playerId,
            String nickname,
            String guestToken
    ) {
        Player player = new Player();

        player.playerId = playerId;
        player.nickname = nickname;
        player.guestToken = guestToken;

        return player;
    }

    // Firebase 로그인 사용자를 처음 Player로 생성
    public static Player createFirebase(
            String playerId,
            String firebaseUid,
            String email
    ) {
        Player player = new Player();

        player.playerId = playerId;
        player.firebaseUid = firebaseUid;
        player.email = email;

        return player;
    }

    // 닉네임 설정 또는 변경
    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }
}