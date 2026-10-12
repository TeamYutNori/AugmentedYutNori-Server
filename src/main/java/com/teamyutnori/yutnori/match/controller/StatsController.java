package com.teamyutnori.yutnori.match.controller;

import com.teamyutnori.yutnori.common.NotFoundException;
import com.teamyutnori.yutnori.match.dto.PlayerStatsResponse;
import com.teamyutnori.yutnori.match.service.MatchRecordService;
import com.teamyutnori.yutnori.player.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/players")
@RequiredArgsConstructor
public class StatsController {

    private final MatchRecordService matchRecordService;
    private final PlayerRepository playerRepository;

    // 플레이어 전적 (판 수·승리 수·승률·최근 10판)
    @GetMapping("/{playerId}/stats")
    public ResponseEntity<PlayerStatsResponse> getStats(@PathVariable String playerId) {
        if (playerRepository.findByPlayerId(playerId).isEmpty()) {
            throw new NotFoundException("PLAYER_NOT_FOUND", "플레이어가 없습니다: " + playerId);
        }
        return ResponseEntity.ok(matchRecordService.getStats(playerId));
    }
}