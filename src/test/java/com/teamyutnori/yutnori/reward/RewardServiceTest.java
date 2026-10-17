package com.teamyutnori.yutnori.reward;

import com.teamyutnori.yutnori.player.entity.Player;
import com.teamyutnori.yutnori.player.repository.PlayerRepository;
import com.teamyutnori.yutnori.ws.dto.WsMessages.RankingEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RewardServiceTest {

    private PlayerRepository playerRepository;
    private RewardService rewardService;
    private final RewardPolicy policy = new RewardPolicy();

    private Player first;
    private Player second;
    private Player left;

    @BeforeEach
    void setUp() {
        playerRepository = mock(PlayerRepository.class);
        rewardService = new RewardService(playerRepository);

        first = Player.createFirebase("p1", "uid-1", "a@example.com");
        second = Player.createFirebase("p2", "uid-2", "b@example.com");
        left = Player.createFirebase("p3", "uid-3", "c@example.com");
        when(playerRepository.findByPlayerId("p1")).thenReturn(Optional.of(first));
        when(playerRepository.findByPlayerId("p2")).thenReturn(Optional.of(second));
        when(playerRepository.findByPlayerId("p3")).thenReturn(Optional.of(left));
    }

    @Test
    void 순위대로_골드를_지급한다() {
        List<RankingEntry> rankings = List.of(
                policy.entry("p1", 0, 1, false, 4),
                policy.entry("p2", 1, 2, false, 4),
                policy.entry("p3", 2, 3, true, 4));   // 나간 플레이어

        int total = rewardService.grant(rankings);

        assertThat(first.getGold()).isEqualTo(500);
        assertThat(second.getGold()).isEqualTo(400);
        assertThat(left.getGold()).isZero();
        assertThat(total).isEqualTo(900);
        verify(playerRepository, never()).findByPlayerId("p3");   // 0골드는 조회도 안 함
    }

    @Test
    void 기존_골드에_더한다() {
        first.addGold(1000);

        rewardService.grant(List.of(policy.entry("p1", 0, 1, false, 4)));

        assertThat(first.getGold()).isEqualTo(1500);
    }

    @Test
    void 없는_플레이어는_건너뛰고_나머지는_지급() {
        when(playerRepository.findByPlayerId("ghost")).thenReturn(Optional.empty());

        int total = rewardService.grant(List.of(
                policy.entry("ghost", 0, 1, false, 4),
                policy.entry("p2", 1, 2, false, 4)));

        assertThat(second.getGold()).isEqualTo(400);
        assertThat(total).isEqualTo(400);
    }

    @Test
    void 빈_목록이면_아무것도_안_한다() {
        assertThat(rewardService.grant(List.of())).isZero();
        assertThat(rewardService.grant(null)).isZero();
        verifyNoInteractions(playerRepository);
    }
}
