package com.teamyutnori.yutnori.reconnect;

import com.teamyutnori.yutnori.game.model.GameEndReason;
import com.teamyutnori.yutnori.ws.RoomBroadcaster;
import com.teamyutnori.yutnori.ws.dto.MessageType;
import com.teamyutnori.yutnori.ws.dto.WsMessages;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
public class DisconnectTimeoutHandler {

    private final Optional<GameEndPort> gameEndPort;
    private final RoomBroadcaster broadcaster;
    private final TurnTimerService turnTimerService;
    private final ConnectionCleanup connectionCleanup;

    public DisconnectTimeoutHandler(Optional<GameEndPort> gameEndPort, RoomBroadcaster broadcaster, TurnTimerService turnTimerService, ConnectionCleanup connectionCleanup) {
        this.gameEndPort = gameEndPort;
        this.broadcaster = broadcaster;
        this.turnTimerService = turnTimerService;
        this.connectionCleanup = connectionCleanup;
    }

    @EventListener
    public void onDisconnectTimeout(DisconnectTimeoutEvent event) {
        String roomCode = event.roomCode();

        if (gameEndPort.isEmpty()) {
            log.warn("GameEndPort 구현이 없어 끊김 기권 처리를 건너뜀 room={} player={}", roomCode, event.playerId());
            return;
        }
        GameEndPort game = gameEndPort.get();

        if (!game.isPlaying(roomCode)) return;

        if (game.removePlayer(roomCode, event.playerId())) {
            log.info("재접속 실패로 플레이어 제거 room={} player={}", roomCode, event.playerId());
            return;
        }

        //TODO: GameService.removePlayer 구현 후 아래 임시 처리 삭제
        forfeitFallback(game, roomCode, event.playerId());
    }

    // removePlayer 구현 전까지 쓰는 임시 처리
    private void forfeitFallback(GameEndPort game, String roomCode, String playerId) {
        Optional<Integer> loserTeam = game.findTeam(roomCode, playerId);
        if (loserTeam.isEmpty()) {
            log.warn("끊긴 플레이어의 팀을 찾을 수 없음 room={} player={}", roomCode, playerId);
            return;
        }

        int winnerTeam = 1 - loserTeam.get();
        game.endGame(roomCode, winnerTeam);
        broadcaster.broadcast(roomCode, MessageType.GAME_ENDED,
                new WsMessages.GameEndedMessage(winnerTeam, GameEndReason.DISCONNECTED));

        turnTimerService.cancel(roomCode);
        connectionCleanup.clearRoom(roomCode);
        log.info("끊김 기권 처리(임시) room={} loser={} winner={}", roomCode, loserTeam.get(), winnerTeam);
    }
}
