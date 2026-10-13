package com.teamyutnori.yutnori.match.service;

import com.teamyutnori.yutnori.game.service.GameFinishedEvent;
import com.teamyutnori.yutnori.match.dto.PlayerStatsResponse;
import com.teamyutnori.yutnori.match.dto.PlayerStatsResponse.RecentMatch;
import com.teamyutnori.yutnori.match.entity.MatchParticipant;
import com.teamyutnori.yutnori.match.entity.MatchResult;
import com.teamyutnori.yutnori.match.repository.MatchParticipantRepository;
import com.teamyutnori.yutnori.match.repository.MatchResultRepository;
import com.teamyutnori.yutnori.ws.dto.WsMessages.RankingEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

// 전적 저장·조회
@Service
@RequiredArgsConstructor
public class MatchRecordService {

    public static final int RECENT_LIMIT = 10;

    private final MatchResultRepository matchResultRepository;
    private final MatchParticipantRepository participantRepository;
    private final Clock clock;

    // 끝난 게임 한 판 저장. 순위표가 비어 있으면 저장하지 않음
    // 나감 여부: GameFinishedEvent.leftPlayerIds (나가기·재접속 실패로 빠진 플레이어)
    @Transactional
    public MatchResult record(GameFinishedEvent event) {
        if (event.rankings().isEmpty()) return null;

        MatchResult match = MatchResult.create(event.roomCode(), event.winnerTeam(), event.reason(), clock.instant());
        for (RankingEntry entry : event.rankings()) {
            boolean left = event.leftPlayerIds().contains(entry.playerId());
            match.addParticipant(MatchParticipant.create(
                    entry.playerId(), entry.team(), entry.rank(), entry.rewardGold(), left));
        }
        return matchResultRepository.save(match);
    }

    // 플레이어 전적 요약 + 최근 판
    @Transactional(readOnly = true)
    public PlayerStatsResponse getStats(String playerId) {
        long total = participantRepository.countByPlayerId(playerId);
        long wins = participantRepository.countByPlayerIdAndRanking(playerId, 1);

        List<RecentMatch> recent = participantRepository
                .findRecent(playerId, PageRequest.of(0, RECENT_LIMIT)).stream()
                .map(p -> new RecentMatch(
                        p.getMatch().getPlayedAt(),
                        p.getRanking(),
                        p.getMatch().getPlayerCount(),
                        p.getRewardGold(),
                        p.isLeftGame(),
                        p.getMatch().getReason()))
                .toList();

        return new PlayerStatsResponse(playerId, total, wins, winRate(wins, total), recent);
    }

    // 0~100, 소수 첫째 자리 반올림
    static double winRate(long wins, long total) {
        if (total == 0) return 0;
        return Math.round(wins * 1000.0 / total) / 10.0;
    }
}