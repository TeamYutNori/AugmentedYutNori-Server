package com.teamyutnori.yutnori.ws.handler;

import com.teamyutnori.yutnori.game.service.GameService;
import com.teamyutnori.yutnori.room.model.Room;
import com.teamyutnori.yutnori.room.service.RoomService;
import com.teamyutnori.yutnori.ws.RoomBroadcaster;
import com.teamyutnori.yutnori.ws.WsMessageContext;
import com.teamyutnori.yutnori.ws.dto.MessageType;
import com.teamyutnori.yutnori.ws.dto.WsMessages.RoomStateMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
@RequiredArgsConstructor
public class LeaveRoomWsHandler implements WsMessageHandler {

    private final RoomService roomService;
    private final RoomBroadcaster broadcaster;
    private final GameService gameService;

    @Override
    public MessageType type() {
        return MessageType.LEAVE_ROOM;
    }

    @Override
    public void handle(
            WsMessageContext context,
            JsonNode payload
    ) {

        // 게임 중이면 게임에서도 뺀다 (PLAYER_REMOVED 전송, 1명 남으면 GAME_ENDED). 게임이 없으면 아무것도 안 함
        gameService.leaveGame(context.roomCode(), context.playerId());

        // 현재 접속한 플레이어를 방에서 제거
        Room room = roomService.leaveRoom(
                context.roomCode(),
                context.playerId()
        );

        // 방에 다른 참가자가 남아 있다면 변경된 방 상태를 전체에게 전송
        if (room.getCurrentPlayers() > 0) {
            broadcaster.broadcast(
                    context.roomCode(),
                    MessageType.ROOM_STATE,
                    RoomStateMessage.from(room)
            );
        }
    }
}