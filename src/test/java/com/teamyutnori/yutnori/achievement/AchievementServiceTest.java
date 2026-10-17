package com.teamyutnori.yutnori.achievement;

import com.teamyutnori.yutnori.achievement.dto.AchievementResponse;
import com.teamyutnori.yutnori.achievement.dto.ClaimAchievementResponse;
import com.teamyutnori.yutnori.common.ConflictException;
import com.teamyutnori.yutnori.common.NotFoundException;
import com.teamyutnori.yutnori.player.entity.Player;
import com.teamyutnori.yutnori.player.repository.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

// 저장소는 메모리 목록으로 흉내 낸다
class AchievementServiceTest {

    private static final AchievementDefinition GAMES_1 = new AchievementDefinition("games_1", "첫 판", "", AchievementStat.GAMES_PLAYED, 1, 100);
    private static final AchievementDefinition GAMES_2 = new AchievementDefinition("games_2", "두 판", "", AchievementStat.GAMES_PLAYED, 2, 300);
    private static final AchievementDefinition LOGIN_2 = new AchievementDefinition("login_2", "이틀", "", AchievementStat.LOGIN_DAYS, 2, 50);

    private final List<PlayerStat> stats = new ArrayList<>();
    private final List<PlayerAchievement> records = new ArrayList<>();
    private Player player;
    private Clock clock = Clock.fixed(Instant.parse("2026-10-13T03:00:00Z"), ZoneOffset.UTC);   // 서울 12:00

    private PlayerStatRepository statRepository;
    private PlayerAchievementRepository achievementRepository;
    private PlayerRepository playerRepository;

    @BeforeEach
    void setUp() {
        statRepository = mock(PlayerStatRepository.class);
        when(statRepository.findByPlayerId(anyString())).thenAnswer(inv ->
                stats.stream().filter(s -> s.getPlayerId().equals(inv.getArgument(0))).findFirst());
        when(statRepository.save(any(PlayerStat.class))).thenAnswer(inv -> {
            stats.add(inv.getArgument(0));
            return inv.getArgument(0);
        });

        achievementRepository = mock(PlayerAchievementRepository.class);
        when(achievementRepository.findByPlayerIdAndAchievementId(anyString(), anyString())).thenAnswer(inv ->
                records.stream().filter(r -> r.getPlayerId().equals(inv.getArgument(0))
                        && r.getAchievementId().equals(inv.getArgument(1))).findFirst());
        when(achievementRepository.findAllByPlayerId(anyString())).thenAnswer(inv ->
                records.stream().filter(r -> r.getPlayerId().equals(inv.getArgument(0))).toList());
        when(achievementRepository.save(any(PlayerAchievement.class))).thenAnswer(inv -> {
            records.add(inv.getArgument(0));
            return inv.getArgument(0);
        });

        player = Player.createFirebase("p1", "uid-1", "a@example.com");
        playerRepository = mock(PlayerRepository.class);
        when(playerRepository.findByPlayerId("p1")).thenReturn(Optional.of(player));
    }

    private AchievementService service() {
        AchievementCatalog catalog = new AchievementCatalog(new AchievementDefinitionFile(1, List.of(GAMES_1, GAMES_2, LOGIN_2)));
        return new AchievementService(catalog, statRepository, achievementRepository, playerRepository, clock);
    }

    private AchievementResponse row(String id) {
        return service().list("p1").achievements().stream().filter(a -> a.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void 기준을_넘으면_달성으로_기록한다() {
        service().record("p1", AchievementStat.GAMES_PLAYED);

        assertThat(records).extracting(PlayerAchievement::getAchievementId).containsExactly("games_1");
        assertThat(row("games_1").achieved()).isTrue();
        assertThat(row("games_2").achieved()).isFalse();
        assertThat(row("games_2").progress()).isEqualTo(1);

        service().record("p1", AchievementStat.GAMES_PLAYED);
        service().record("p1", AchievementStat.GAMES_PLAYED);   // 3판: 진행도는 goal까지만

        assertThat(records).extracting(PlayerAchievement::getAchievementId).containsExactly("games_1", "games_2");
        assertThat(row("games_2").progress()).isEqualTo(2);
    }

    @Test
    void 한_번에_여러_횟수를_올리면_넘은_업적을_모두_달성한다() {
        service().record("p1", AchievementStat.GAMES_PLAYED, 10);   // 10연차 뽑기처럼 한 번에 +10

        assertThat(records).extracting(PlayerAchievement::getAchievementId).containsExactly("games_1", "games_2");
        assertThat(stats.get(0).get(AchievementStat.GAMES_PLAYED)).isEqualTo(10);
    }

    @Test
    void 접속은_하루_한_번만_센다() {
        service().recordLogin("p1");
        service().recordLogin("p1");
        assertThat(row("login_2").progress()).isEqualTo(1);

        clock = Clock.offset(clock, java.time.Duration.ofDays(1));
        service().recordLogin("p1");
        assertThat(row("login_2").achieved()).isTrue();
    }

    @Test
    void 기록이_없으면_진행도_0() {
        assertThat(service().list("p1").achievements()).extracting(AchievementResponse::progress).containsOnly(0);
    }

    @Test
    void 보상을_받으면_골드가_오르고_다시_못_받는다() {
        service().record("p1", AchievementStat.GAMES_PLAYED);

        ClaimAchievementResponse claimed = service().claim("p1", "games_1");

        assertThat(claimed.rewardGold()).isEqualTo(100);
        assertThat(claimed.gold()).isEqualTo(100);
        assertThat(player.getGold()).isEqualTo(100);
        assertThat(row("games_1").claimed()).isTrue();
        assertThatThrownBy(() -> service().claim("p1", "games_1"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void 달성_전이거나_없는_업적은_받을_수_없다() {
        assertThatThrownBy(() -> service().claim("p1", "games_1")).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service().claim("p1", "nope")).isInstanceOf(NotFoundException.class);
        assertThat(player.getGold()).isZero();
    }
}
