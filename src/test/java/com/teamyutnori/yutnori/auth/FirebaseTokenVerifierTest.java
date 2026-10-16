package com.teamyutnori.yutnori.auth;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FirebaseTokenVerifierTest {

    @Test
    void sign_in_provider가_anonymous면_익명_로그인() {
        assertThat(FirebaseTokenVerifier.isAnonymousProvider(
                Map.of("sign_in_provider", "anonymous"))).isTrue();
    }

    @Test
    void 구글이나_값이_없으면_익명이_아니다() {
        assertThat(FirebaseTokenVerifier.isAnonymousProvider(
                Map.of("sign_in_provider", "google.com"))).isFalse();
        assertThat(FirebaseTokenVerifier.isAnonymousProvider(Map.of())).isFalse();
        assertThat(FirebaseTokenVerifier.isAnonymousProvider(null)).isFalse();
        assertThat(FirebaseTokenVerifier.isAnonymousProvider("anonymous")).isFalse();
    }
}
