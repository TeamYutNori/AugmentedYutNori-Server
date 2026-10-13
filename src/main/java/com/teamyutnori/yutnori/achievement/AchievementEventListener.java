package com.teamyutnori.yutnori.achievement;

import com.teamyutnori.yutnori.auth.PlayerLoggedInEvent;
import com.teamyutnori.yutnori.gacha.GachaDrawnEvent;
import com.teamyutnori.yutnori.game.service.GameFinishedEvent;
import com.teamyutnori.yutnori.ws.dto.WsMessages.RankingEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

// 업적 횟수를 올리는 곳 연결
//  - 접속   : PlayerLoggedInEvent (AuthController)       → 하루 1번만 +1
//  - 플레이 : GameFinishedEvent (GameService.finishGame) → 끝까지 남은 플레이어 +1, 1등이면 1등 횟수도 +1
//  - 뽑기   : GachaDrawnEvent (GachaService)             → 뽑기가 커밋된 뒤 +1 (실패한 뽑기는 안 셈)
// 집계가 실패해도 로그인·게임 종료·뽑기는 그대로 진행되도록 예외는 로그로만 남긴다
@Slf4j
@Component
@RequiredArgsConstructor
public class AchievementEventListener {

    private final AchievementService achievementService;

    @EventListener
    public void onLoggedIn(PlayerLoggedInEvent event) {
        try {
            achievementService.recordLogin(event.playerId());
        } catch (RuntimeException e) {
            log.error("[Achievement] 접속 집계 실패 player={}", event.playerId(), e);
        }
    }

    @EventListener
    public void onGameFinished(GameFinishedEvent event) {
        for (RankingEntry entry : event.rankings()) {
            if (event.leftPlayerIds().contains(entry.playerId())) continue;   // 중간에 나간 판은 안 셈
            try {
                achievementService.record(entry.playerId(), AchievementStat.GAMES_PLAYED);
                if (entry.rank() == 1) achievementService.record(entry.playerId(), AchievementStat.FIRST_PLACES);
            } catch (RuntimeException e) {
                log.error("[Achievement] 게임 종료 집계 실패 room={} player={}", event.roomCode(), entry.playerId(), e);
            }
        }
    }

    @TransactionalEventListener
    public void onGachaDrawn(GachaDrawnEvent event) {
        try {
            achievementService.record(event.playerId(), AchievementStat.GACHA_DRAWS);
        } catch (RuntimeException e) {
            log.error("[Achievement] 뽑기 집계 실패 player={}", event.playerId(), e);
        }
    }
}
