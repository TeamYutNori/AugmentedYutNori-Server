package com.teamyutnori.yutnori.player.service;

import com.teamyutnori.yutnori.player.entity.Player;
import com.teamyutnori.yutnori.player.repository.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PlayerServiceLoginTest {

    private static final Instant NOW = Instant.parse("2026-10-17T00:00:00Z");

    private PlayerRepository playerRepository;
    private PlayerService playerService;

    @BeforeEach
    void setUp() {
        playerRepository = mock(PlayerRepository.class);
        when(playerRepository.findByFirebaseUid(any())).thenReturn(Optional.empty());
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));
        playerService = new PlayerService(playerRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void 익명_토큰으로_처음_로그인하면_게스트로_만든다() {
        Player player = playerService.findOrCreateByFirebase("anon-uid", null, true);

        assertThat(player.isGuest()).isTrue();
        assertThat(player.getLastLoginAt()).isEqualTo(NOW);
        verify(playerRepository).save(player);
    }

    @Test
    void 구글_토큰으로_처음_로그인하면_일반_계정() {
        Player player = playerService.findOrCreateByFirebase("uid", "a@example.com", false);

        assertThat(player.isGuest()).isFalse();
        assertThat(player.getEmail()).isEqualTo("a@example.com");
        assertThat(player.getLastLoginAt()).isEqualTo(NOW);
    }

    @Test
    void 기존_계정은_새로_만들지_않고_접속_시각만_갱신한다() {
        Player existing = Player.createFirebaseGuest("p1", "anon-uid");
        existing.recordLogin(NOW.minusSeconds(86400));
        when(playerRepository.findByFirebaseUid("anon-uid")).thenReturn(Optional.of(existing));

        Player player = playerService.findOrCreateByFirebase("anon-uid", null, true);

        assertThat(player).isSameAs(existing);
        assertThat(player.isGuest()).isTrue();
        assertThat(player.getLastLoginAt()).isEqualTo(NOW);
        verify(playerRepository, never()).save(any());
    }

    @Test
    void 게스트_UID에_구글이_연결돼_들어오면_일반_계정으로_바꾼다() {
        Player existing = Player.createFirebaseGuest("p1", "anon-uid");
        when(playerRepository.findByFirebaseUid("anon-uid")).thenReturn(Optional.of(existing));

        Player player = playerService.findOrCreateByFirebase("anon-uid", "a@example.com", false);

        assertThat(player.isGuest()).isFalse();
        assertThat(player.getEmail()).isEqualTo("a@example.com");
        assertThat(player.getPlayerId()).isEqualTo("p1");
    }
}
