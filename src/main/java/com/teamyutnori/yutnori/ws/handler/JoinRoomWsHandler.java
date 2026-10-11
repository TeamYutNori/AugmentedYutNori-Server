package com.teamyutnori.yutnori.ws.handler;

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
public class JoinRoomWsHandler implements WsMessageHandler {

    private final RoomService roomService;
    private final RoomBroadcaster broadcaster;

    @Override
    public MessageType type() {
        return MessageType.JOIN_ROOM;
    }

    @Override
    public void handle(
            WsMessageContext context,
            JsonNode payload
    ) {

        // WebSocket 인증 과정에서 확인된 플레이어 정보로 방 참가 처리
        Room room = roomService.joinRoom(
                context.roomCode(),
                context.playerId(),
                context.nickname()
        );

        // 참가자가 바뀌었으므로 현재 방 상태를 모든 참가자에게 알림
        broadcaster.broadcast(
                context.roomCode(),
                MessageType.ROOM_STATE,
                RoomStateMessage.from(room)
        );
    }
}