package com.teamyutnori.yutnori.match.dto;

import com.teamyutnori.yutnori.game.model.GameEndReason;

import java.time.Instant;
import java.util.List;

// GET /players/{playerId}/stats 응답
// winRate: 0~100 (소수 첫째 자리 반올림), 판이 없으면 0
public record PlayerStatsResponse(
        String playerId,
        long totalGames,
        long wins,
        double winRate,
        List<RecentMatch> recentMatches
) {
    // 최근 한 판
    public record RecentMatch(
            Instant playedAt,
            int rank,
            int playerCount,
            int rewardGold,
            boolean leftGame,
            GameEndReason reason
    ) {}
}