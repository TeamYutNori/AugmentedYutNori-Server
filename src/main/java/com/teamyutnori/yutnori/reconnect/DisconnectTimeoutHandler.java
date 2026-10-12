package com.teamyutnori.yutnori.reconnect;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
public class DisconnectTimeoutHandler {

    private final Optional<GameEndPort> gameEndPort;

    public DisconnectTimeoutHandler(Optional<GameEndPort> gameEndPort) {
        this.gameEndPort = gameEndPort;
    }

    @EventListener
    public void onDisconnectTimeout(DisconnectTimeoutEvent event) {
        String roomCode = event.roomCode();
        if (gameEndPort.isEmpty()) {
            log.warn("GameEndPort 구현이 없어 끊김 처리를 건너뜀 room={} player={}", roomCode, event.playerId());
            return;
        }
        GameEndPort game = gameEndPort.get();
        if (!game.isPlaying(roomCode)) return;

        if (game.removePlayer(roomCode, event.playerId())) {
            log.info("재접속 실패로 플레이어 제거 room={} player={}", roomCode, event.playerId());
        } else {
            log.warn("게임 참가자가 아니라 제거하지 못함 room={} player={}", roomCode, event.playerId());
        }
    }
}
