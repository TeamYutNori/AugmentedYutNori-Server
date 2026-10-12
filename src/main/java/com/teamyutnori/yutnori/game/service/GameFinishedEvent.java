package com.teamyutnori.yutnori.game.service;

import com.teamyutnori.yutnori.game.model.GameEndReason;
import com.teamyutnori.yutnori.ws.dto.WsMessages.RankingEntry;

import java.util.List;

// 게임이 끝나면 GameService.finishGame이 발행한다 (업적: 플레이 횟수·1등 횟수 등 집계용)
// 받는 쪽: @EventListener void on(GameFinishedEvent e)
// winnerTeam: 남은 팀이 없이 끝나면 -1
public record GameFinishedEvent(String roomCode, int winnerTeam, GameEndReason reason, List<RankingEntry> rankings) {
    public GameFinishedEvent {
        rankings = rankings == null ? List.of() : List.copyOf(rankings);
    }
}
