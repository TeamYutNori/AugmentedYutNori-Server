package com.teamyutnori.yutnori.match.service;

import com.teamyutnori.yutnori.game.service.GameFinishedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// GameService.finishGame이 발행하는 GameFinishedEvent를 받아 전적 저장
// 이벤트는 동기 발행이라 여기서 예외가 나면 게임 처리까지 실패하므로, 저장 실패는 로그만 남긴다
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchRecordListener {

    private final MatchRecordService matchRecordService;

    @EventListener
    public void onGameFinished(GameFinishedEvent event) {
        try {
            matchRecordService.record(event);
        } catch (RuntimeException e) {
            log.error("[Match] 전적 저장 실패 room={} rankings={}", event.roomCode(), event.rankings(), e);
        }
    }
}