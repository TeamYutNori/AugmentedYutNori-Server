package com.teamyutnori.yutnori.reconnect;

import com.teamyutnori.yutnori.common.NotFoundException;
import com.teamyutnori.yutnori.room.model.Room;
import com.teamyutnori.yutnori.room.model.RoomStatus;
import com.teamyutnori.yutnori.room.service.RoomService;
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
    private final RoomService roomService;
    private final RoomBroadcaster broadcaster;

    public DisconnectTimeoutHandler(
            Optional<GameEndPort> gameEndPort,
            RoomService roomService,
            RoomBroadcaster broadcaster
    ) {
        this.gameEndPort = gameEndPort;
        this.roomService = roomService;
        this.broadcaster = broadcaster;
    }

    @EventListener
    public void onDisconnectTimeout(DisconnectTimeoutEvent event) {
        String roomCode = event.roomCode();
        String playerId = event.playerId();

        // 대기방에서 재접속하지 못한 플레이어 처리
        try {
            Room room = roomService.getRoomOrThrow(roomCode);

            if (room.getStatus() == RoomStatus.WAITING) {
                // 이미 정상적으로 방을 나간 플레이어라면 아무 처리하지 않음
                if (!room.containsMember(playerId)) {
                    log.info("이미 대기방에서 나간 플레이어이므로 끊김 처리 생략 room={} player={}", roomCode, playerId);
                    return;
                }

                Room updatedRoom = roomService.leaveRoom(roomCode, playerId);
                broadcaster.broadcast(roomCode, MessageType.ROOM_STATE, WsMessages.RoomStateMessage.from(updatedRoom));
                log.info("대기방 재접속 실패로 플레이어 제거 room={} player={}", roomCode, playerId);
                return;
            }
        } catch (NotFoundException e) {
            // 이미 방이 삭제된 경우 추가 처리하지 않음
            log.info("이미 삭제된 방이므로 끊김 처리 생략 room={} player={}", roomCode, playerId);
            return;
        }

        // 게임 중 재접속 실패 처리
        if (gameEndPort.isEmpty()) {
            log.warn("GameEndPort 구현이 없어 끊김 처리를 건너뜀 room={} player={}", roomCode, playerId);
            return;
        }
        GameEndPort game = gameEndPort.get();

        if (!game.isPlaying(roomCode)) {
            return;
        }

        if (game.removePlayer(roomCode, playerId)) {
            log.info("재접속 실패로 플레이어 제거 room={} player={}", roomCode, playerId);
        } else {
            log.warn("게임 참가자가 아니라 제거하지 못함 room={} player={}", roomCode, playerId);
        }
    }
}