package com.teamyutnori.yutnori.player.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.teamyutnori.yutnori.achievement.PlayerAchievementRepository;
import com.teamyutnori.yutnori.achievement.PlayerStatRepository;
import com.teamyutnori.yutnori.common.ConflictException;
import com.teamyutnori.yutnori.common.NotFoundException;
import com.teamyutnori.yutnori.icon.PlayerIconRepository;
import com.teamyutnori.yutnori.player.PlayerDataRemover;
import com.teamyutnori.yutnori.player.entity.Player;
import com.teamyutnori.yutnori.player.repository.PlayerRepository;
import com.teamyutnori.yutnori.room.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class AccountWithdrawalServiceTest {

    private PlayerRepository playerRepository;
    private PlayerStatRepository playerStatRepository;
    private PlayerAchievementRepository playerAchievementRepository;
    private PlayerIconRepository playerIconRepository;
    private RoomRepository roomRepository;
    private FirebaseAuth firebaseAuth;
    private AccountWithdrawalService service;

    @BeforeEach
    void setUp() {
        playerRepository = mock(PlayerRepository.class);
        playerStatRepository = mock(PlayerStatRepository.class);
        playerAchievementRepository = mock(PlayerAchievementRepository.class);
        playerIconRepository = mock(PlayerIconRepository.class);
        roomRepository = mock(RoomRepository.class);
        firebaseAuth = mock(FirebaseAuth.class);
        PlayerDataRemover remover = new PlayerDataRemover(
                playerRepository, playerStatRepository, playerAchievementRepository, playerIconRepository, firebaseAuth);
        service = new AccountWithdrawalService(
                playerRepository, roomRepository, remover, TransactionOperations.withoutTransaction());
    }

    @Test
    void 구글_계정을_탈퇴하면_계정_데이터와_Firebase_계정을_모두_지운다() throws Exception {
        Player player = Player.createFirebase("p1", "uid-1", "a@example.com");
        when(playerRepository.findByPlayerId("p1")).thenReturn(Optional.of(player));

        service.withdraw("p1");

        verify(playerStatRepository).deleteByPlayerId("p1");
        verify(playerAchievementRepository).deleteByPlayerId("p1");
        verify(playerIconRepository).deleteByPlayerId("p1");
        verify(playerRepository).delete(player);
        verify(firebaseAuth).deleteUsers(List.of("uid-1"));
    }

    @Test
    void 게스트도_같은_방식으로_탈퇴한다() throws Exception {
        Player guest = Player.createFirebaseGuest("p2", "anon-uid");
        when(playerRepository.findByPlayerId("p2")).thenReturn(Optional.of(guest));

        service.withdraw("p2");

        verify(playerRepository).delete(guest);
        verify(firebaseAuth).deleteUsers(List.of("anon-uid"));
    }

    @Test
    void 방이나_게임에_참가_중이면_409이고_아무것도_지우지_않는다() throws Exception {
        Player player = Player.createFirebase("p1", "uid-1", "a@example.com");
        when(playerRepository.findByPlayerId("p1")).thenReturn(Optional.of(player));
        when(roomRepository.containsPlayer("p1")).thenReturn(true);

        assertThatThrownBy(() -> service.withdraw("p1"))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "IN_ROOM");
        verify(playerRepository, never()).delete(any());
        verify(firebaseAuth, never()).deleteUsers(anyList());
    }

    @Test
    void 없는_플레이어는_404() {
        when(playerRepository.findByPlayerId("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.withdraw("ghost"))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "PLAYER_NOT_FOUND");
    }

    @Test
    void Firebase_삭제가_실패해도_DB는_지워지고_예외를_던지지_않는다() throws Exception {
        Player player = Player.createFirebase("p1", "uid-1", "a@example.com");
        when(playerRepository.findByPlayerId("p1")).thenReturn(Optional.of(player));
        when(firebaseAuth.deleteUsers(anyList())).thenThrow(mock(FirebaseAuthException.class));

        assertThatCode(() -> service.withdraw("p1")).doesNotThrowAnyException();
        verify(playerRepository).delete(player);
    }

    @Test
    void 예전_에디터_테스트용_게스트는_Firebase_계정이_없어서_DB만_지운다() throws Exception {
        Player editorGuest = Player.createGuest("p3", "테스트", "guest-token");
        when(playerRepository.findByPlayerId("p3")).thenReturn(Optional.of(editorGuest));

        service.withdraw("p3");

        verify(playerRepository).delete(editorGuest);
        verify(firebaseAuth, never()).deleteUsers(anyList());
    }
}
