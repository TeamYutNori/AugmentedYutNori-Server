package com.teamyutnori.yutnori.player.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerGuestTest {

    @Test
    void 익명_로그인_플레이어는_게스트로_만든다() {
        Player guest = Player.createFirebaseGuest("p1", "anon-uid");

        assertThat(guest.isGuest()).isTrue();
        assertThat(guest.getFirebaseUid()).isEqualTo("anon-uid");
        assertThat(guest.getEmail()).isNull();
        assertThat(guest.getGuestToken()).isNull();   // 예전 에디터 테스트용 게스트와 별개
    }

    @Test
    void 구글_로그인_플레이어는_게스트가_아니다() {
        Player player = Player.createFirebase("p1", "uid", "a@example.com");

        assertThat(player.isGuest()).isFalse();
    }

    @Test
    void 로그인_시각을_기록한다() {
        Player guest = Player.createFirebaseGuest("p1", "anon-uid");
        Instant now = Instant.parse("2026-10-17T00:00:00Z");

        guest.recordLogin(now);

        assertThat(guest.getLastLoginAt()).isEqualTo(now);
    }

    @Test
    void 구글로_전환하면_게스트가_아니고_이메일이_생긴다() {
        Player guest = Player.createFirebaseGuest("p1", "anon-uid");

        guest.upgradeToGoogle("a@example.com");

        assertThat(guest.isGuest()).isFalse();
        assertThat(guest.getEmail()).isEqualTo("a@example.com");
        assertThat(guest.getPlayerId()).isEqualTo("p1");   // 같은 계정 그대로 (골드·업적 유지)
    }
}
