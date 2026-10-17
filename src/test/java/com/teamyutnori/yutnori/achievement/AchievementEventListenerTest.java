package com.teamyutnori.yutnori.achievement;

import com.teamyutnori.yutnori.auth.PlayerLoggedInEvent;
import com.teamyutnori.yutnori.gacha.GachaDrawnEvent;
import com.teamyutnori.yutnori.game.model.GameEndReason;
import com.teamyutnori.yutnori.game.service.GameFinishedEvent;
import com.teamyutnori.yutnori.ws.dto.WsMessages.RankingEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AchievementEventListenerTest {

    private AchievementService service;
    private AchievementEventListener listener;

    @BeforeEach
    void setUp() {
        service = mock(AchievementService.class);
        listener = new AchievementEventListener(service);
    }

    @Test
    void 게임이_끝나면_남은_사람만_플레이_횟수_1등은_1등_횟수도() {
        listener.onGameFinished(new GameFinishedEvent("ROOM", 0, GameEndReason.FINISHED, List.of(
                new RankingEntry("p0", 0, 1, 500),
                new RankingEntry("p1", 1, 2, 400),
                new RankingEntry("p2", 2, 3, 0)), Set.of("p2")));

        verify(service).record("p0", AchievementStat.GAMES_PLAYED);
        verify(service).record("p0", AchievementStat.FIRST_PLACES);
        verify(service).record("p1", AchievementStat.GAMES_PLAYED);
        verify(service, never()).record(eq("p1"), eq(AchievementStat.FIRST_PLACES));
        verify(service, never()).record(eq("p2"), any());
    }

    @Test
    void 한_명이_실패해도_다른_사람은_센다() {
        doThrow(new RuntimeException("db")).when(service).record("p0", AchievementStat.GAMES_PLAYED);

        listener.onGameFinished(new GameFinishedEvent("ROOM", 0, GameEndReason.FINISHED, List.of(
                new RankingEntry("p0", 0, 1, 500),
                new RankingEntry("p1", 1, 2, 400)), Set.of()));

        verify(service).record("p1", AchievementStat.GAMES_PLAYED);
    }

    @Test
    void 접속과_뽑기() {
        listener.onLoggedIn(new PlayerLoggedInEvent("p0"));
        listener.onGachaDrawn(new GachaDrawnEvent("p0", 10));

        verify(service).recordLogin("p0");
        verify(service).record("p0", AchievementStat.GACHA_DRAWS, 10);   // 10연차는 한 번에 +10
    }

    @Test
    void 집계가_실패해도_예외를_밖으로_던지지_않는다() {
        doThrow(new RuntimeException("db")).when(service).recordLogin(anyString());

        listener.onLoggedIn(new PlayerLoggedInEvent("p0"));   // 로그인은 그대로 성공해야 한다
    }
}
