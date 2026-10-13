package com.teamyutnori.yutnori.match.service;

import com.teamyutnori.yutnori.game.model.GameEndReason;
import com.teamyutnori.yutnori.game.service.GameFinishedEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MatchRecordListenerTest {

    private final GameFinishedEvent event =
            new GameFinishedEvent("ROOM1", 0, GameEndReason.FINISHED, List.of(), Set.of());

    // 게임이 끝나면 전적 저장을 부른다
    @Test
    void records() {
        MatchRecordService service = mock(MatchRecordService.class);

        new MatchRecordListener(service).onGameFinished(event);

        verify(service).record(event);
    }

    // 저장이 실패해도 예외를 밖으로 던지지 않는다 (게임 종료 처리는 계속)
    @Test
    void saveFails() {
        MatchRecordService service = mock(MatchRecordService.class);
        when(service.record(any())).thenThrow(new RuntimeException("DB 오류"));

        assertThatCode(() -> new MatchRecordListener(service).onGameFinished(event))
                .doesNotThrowAnyException();
    }
}