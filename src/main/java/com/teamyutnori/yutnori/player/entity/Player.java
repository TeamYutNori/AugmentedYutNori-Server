package com.teamyutnori.yutnori.player.entity;

import com.teamyutnori.yutnori.common.ConflictException;
import com.teamyutnori.yutnori.common.InvalidRequestException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

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

    // 보유 골드. 게임 순위 보상으로 받고 뽑기 등에 쓴다. 0 미만이 될 수 없음
    // 기존 플레이어는 컬럼이 추가될 때 0으로 채워진다
    @Column(nullable = false)
    @ColumnDefault("0")
    private int gold;

    // 프로필에 표시할 아이콘 id (profile-icons.json의 id). 아직 고르지 않았으면 값이 없음
    @Column(length = 64)
    private String selectedIconId;

    // Firebase 익명 로그인("게스트로 시작")으로 만든 계정이면 true.
    // 오래 접속하지 않으면 GuestCleanupService가 지운다. 예전 에디터 테스트용 게스트(guestToken)와는 별개
    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean guest;

    // 마지막으로 /auth/firebase 로그인한 시각. 게스트 정리 기준
    @Column
    private Instant lastLoginAt;

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

    // Firebase 익명 로그인 사용자를 처음 Player로 생성 (게스트, 이메일 없음)
    public static Player createFirebaseGuest(
            String playerId,
            String firebaseUid
    ) {
        Player player = createFirebase(playerId, firebaseUid, null);
        player.guest = true;
        return player;
    }

    // 로그인할 때마다 마지막 접속 시각 기록
    public void recordLogin(Instant now) {
        this.lastLoginAt = now;
    }

    // 게스트 계정이 구글 계정으로 바뀜 (같은 Firebase UID에 구글이 연결된 경우). 이후 정리 대상에서 빠진다
    public void upgradeToGoogle(String email) {
        this.guest = false;
        this.email = email;
    }

    // 닉네임 설정 또는 변경
    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    // 프로필 아이콘 선택. 보유 여부 확인은 부르는 쪽(내 정보 아이콘 변경)에서 한다
    public void selectIcon(String iconId) {
        this.selectedIconId = iconId;
    }

    // 골드 지급 (게임 보상 등)
    public void addGold(int amount) {
        validateGoldAmount(amount);
        this.gold = Math.addExact(this.gold, amount);
    }

    // 골드 차감 (뽑기 등). 부족하면 차감하지 않고 409로 알린다
    public void spendGold(int amount) {
        validateGoldAmount(amount);
        if (gold < amount) {
            throw new ConflictException(
                    "NOT_ENOUGH_GOLD",
                    "골드가 부족합니다. (보유 " + gold + ", 필요 " + amount + ")"
            );
        }
        this.gold -= amount;
    }

    private static void validateGoldAmount(int amount) {
        if (amount < 0) {
            throw new InvalidRequestException(
                    "INVALID_GOLD_AMOUNT",
                    "골드 수량은 0 이상이어야 합니다: " + amount
            );
        }
    }
}