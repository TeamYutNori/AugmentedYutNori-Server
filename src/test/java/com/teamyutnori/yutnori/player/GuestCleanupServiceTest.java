package com.teamyutnori.yutnori.player;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.teamyutnori.yutnori.achievement.PlayerAchievementRepository;
import com.teamyutnori.yutnori.achievement.PlayerStatRepository;
import com.teamyutnori.yutnori.icon.PlayerIconRepository;
import com.teamyutnori.yutnori.player.entity.Player;
import com.teamyutnori.yutnori.player.repository.PlayerRepository;
import com.teamyutnori.yutnori.room.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class GuestCleanupServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-17T00:00:00Z");

    private PlayerRepository playerRepository;
    private PlayerStatRepository playerStatRepository;
    private PlayerAchievementRepository playerAchievementRepository;
    private PlayerIconRepository playerIconRepository;
    private RoomRepository roomRepository;
    private FirebaseAuth firebaseAuth;
    private GuestCleanupService service;

    @BeforeEach
    void setUp() {
        playerRepository = mock(PlayerRepository.class);
        playerStatRepository = mock(PlayerStatRepository.class);
        playerAchievementRepository = mock(PlayerAchievementRepository.class);
        playerIconRepository = mock(PlayerIconRepository.class);
        roomRepository = mock(RoomRepository.class);
        firebaseAuth = mock(FirebaseAuth.class);
        service = new GuestCleanupService(
                playerRepository, playerStatRepository, playerAchievementRepository, playerIconRepository,
                roomRepository, firebaseAuth, TransactionOperations.withoutTransaction(),
                Clock.fixed(NOW, ZoneOffset.UTC), 30);
    }

    private static Player guest(String playerId, String uid) {
        return Player.createFirebaseGuest(playerId, uid);
    }

    @Test
    void 기준일은_지금부터_30일_전이다() {
        when(playerRepository.findByGuestTrueAndLastLoginAtBefore(any())).thenReturn(List.of());

        service.cleanup();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(playerRepository).findByGuestTrueAndLastLoginAtBefore(cutoff.capture());
        assertThat(cutoff.getValue()).isEqualTo(NOW.minus(Duration.ofDays(30)));
    }

    @Test
    void 오래된_게스트의_데이터와_Firebase_계정을_지운다() throws Exception {
        Player old = guest("p1", "uid-1");
        when(playerRepository.findByGuestTrueAndLastLoginAtBefore(any())).thenReturn(List.of(old));

        int deleted = service.cleanup();

        assertThat(deleted).isEqualTo(1);
        verify(playerStatRepository).deleteByPlayerId("p1");
        verify(playerAchievementRepository).deleteByPlayerId("p1");
        verify(playerIconRepository).deleteByPlayerId("p1");
        verify(playerRepository).delete(old);
        verify(firebaseAuth).deleteUsers(List.of("uid-1"));
    }

    @Test
    void 방이나_게임에_들어가_있는_게스트는_건너뛴다() throws Exception {
        Player playing = guest("p1", "uid-1");
        Player idle = guest("p2", "uid-2");
        when(playerRepository.findByGuestTrueAndLastLoginAtBefore(any())).thenReturn(List.of(playing, idle));
        when(roomRepository.containsPlayer("p1")).thenReturn(true);

        int deleted = service.cleanup();

        assertThat(deleted).isEqualTo(1);
        verify(playerRepository, never()).delete(playing);
        verify(playerStatRepository, never()).deleteByPlayerId("p1");
        verify(playerRepository).delete(idle);
        verify(firebaseAuth).deleteUsers(List.of("uid-2"));
    }

    @Test
    void 대상이_없으면_아무것도_지우지_않는다() throws Exception {
        when(playerRepository.findByGuestTrueAndLastLoginAtBefore(any())).thenReturn(List.of());

        assertThat(service.cleanup()).isZero();

        verify(playerRepository, never()).delete(any());
        verify(firebaseAuth, never()).deleteUsers(anyList());
    }

    @Test
    void Firebase_삭제가_실패해도_DB_정리는_끝난다() throws Exception {
        Player old = guest("p1", "uid-1");
        when(playerRepository.findByGuestTrueAndLastLoginAtBefore(any())).thenReturn(List.of(old));
        when(firebaseAuth.deleteUsers(anyList())).thenThrow(mock(FirebaseAuthException.class));

        assertThat(service.cleanup()).isEqualTo(1);
        verify(playerRepository).delete(old);
    }
}
