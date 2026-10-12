package com.teamyutnori.yutnori.match.service;

import com.teamyutnori.yutnori.game.model.GameEndReason;
import com.teamyutnori.yutnori.game.service.GameFinishedEvent;
import com.teamyutnori.yutnori.match.dto.PlayerStatsResponse;
import com.teamyutnori.yutnori.match.entity.MatchParticipant;
import com.teamyutnori.yutnori.match.entity.MatchResult;
import com.teamyutnori.yutnori.match.repository.MatchParticipantRepository;
import com.teamyutnori.yutnori.match.repository.MatchResultRepository;
import com.teamyutnori.yutnori.ws.dto.WsMessages.RankingEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MatchRecordServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");

    private MatchResultRepository matchResultRepository;
    private MatchParticipantRepository participantRepository;
    private MatchRecordService service;

    @BeforeEach
    void setUp() {
        matchResultRepository = mock(MatchResultRepository.class);
        participantRepository = mock(MatchParticipantRepository.class);
        when(matchResultRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new MatchRecordService(matchResultRepository, participantRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // 순위표 그대로 한 판과 참가자들이 저장된다 (0골드 = 나감)
    @Test
    void record() {
        GameFinishedEvent event = new GameFinishedEvent("ROOM1", 0, GameEndReason.FINISHED, List.of(
                new RankingEntry("p0", 0, 1, 500),
                new RankingEntry("p1", 1, 2, 400),
                new RankingEntry("p2", 2, 3, 0)));

        MatchResult saved = service.record(event);

        assertThat(saved.getRoomCode()).isEqualTo("ROOM1");
        assertThat(saved.getWinnerTeam()).isZero();
        assertThat(saved.getPlayedAt()).isEqualTo(NOW);
        assertThat(saved.getPlayerCount()).isEqualTo(3);
        assertThat(saved.getParticipants()).extracting(MatchParticipant::getPlayerId).containsExactly("p0", "p1", "p2");
        assertThat(saved.getParticipants()).extracting(MatchParticipant::isLeftGame).containsExactly(false, false, true);
        assertThat(saved.getParticipants().get(0).getMatch()).isSameAs(saved);
    }

    // 순위표가 비어 있으면 저장하지 않는다
    @Test
    void emptyRankings() {
        service.record(new GameFinishedEvent("ROOM1", -1, GameEndReason.DISCONNECTED, List.of()));

        verifyNoInteractions(matchResultRepository);
    }

    // 판 수·승리 수·승률을 계산한다
    @Test
    void stats() {
        when(participantRepository.countByPlayerId("p0")).thenReturn(3L);
        when(participantRepository.countByPlayerIdAndRanking("p0", 1)).thenReturn(2L);
        when(participantRepository.findRecent(eq("p0"), any())).thenReturn(List.of());

        PlayerStatsResponse stats = service.getStats("p0");

        assertThat(stats.totalGames()).isEqualTo(3);
        assertThat(stats.wins()).isEqualTo(2);
        assertThat(stats.winRate()).isEqualTo(66.7);
    }

    // 최근 전적에 판 정보가 담긴다
    @Test
    void recent() {
        MatchResult match = MatchResult.create("ROOM1", 1, GameEndReason.FINISHED, NOW);
        MatchParticipant mine = MatchParticipant.create("p0", 0, 2, 400, false);
        match.addParticipant(mine);
        match.addParticipant(MatchParticipant.create("p1", 1, 1, 500, false));
        when(participantRepository.findRecent(eq("p0"), any())).thenReturn(List.of(mine));

        PlayerStatsResponse.RecentMatch recent = service.getStats("p0").recentMatches().get(0);

        assertThat(recent.playedAt()).isEqualTo(NOW);
        assertThat(recent.rank()).isEqualTo(2);
        assertThat(recent.playerCount()).isEqualTo(2);
        assertThat(recent.rewardGold()).isEqualTo(400);
        assertThat(recent.reason()).isEqualTo(GameEndReason.FINISHED);
    }

    // 판이 없으면 승률 0, 반올림은 소수 첫째 자리
    @Test
    void winRate() {
        assertThat(MatchRecordService.winRate(0, 0)).isZero();
        assertThat(MatchRecordService.winRate(1, 3)).isEqualTo(33.3);
        assertThat(MatchRecordService.winRate(2, 2)).isEqualTo(100.0);
    }
}