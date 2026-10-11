package com.teamyutnori.yutnori.ws.handler;

import com.teamyutnori.yutnori.room.model.Room;
import com.teamyutnori.yutnori.room.service.RoomService;
import com.teamyutnori.yutnori.ws.RoomBroadcaster;
import com.teamyutnori.yutnori.ws.WsMessageContext;
import com.teamyutnori.yutnori.ws.dto.MessageType;
import com.teamyutnori.yutnori.ws.dto.WsMessages.RoomStateMessage;
import com.teamyutnori.yutnori.ws.dto.WsRequests.ReadyMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class ReadyWsHandler implements WsMessageHandler {

    private final RoomService roomService;
    private final RoomBroadcaster broadcaster;
    private final ObjectMapper objectMapper;

    @Override
    public MessageType type() {
        return MessageType.READY;
    }

    @Override
    public void handle(
            WsMessageContext context,
            JsonNode payload
    ) {

        // READY payload의 true/false 값을 객체로 변환
        ReadyMessage request =
                objectMapper.treeToValue(
                        payload,
                        ReadyMessage.class
                );

        // 현재 플레이어의 준비 상태 변경
        Room room = roomService.changeReady(
                context.roomCode(),
                context.playerId(),
                request.ready()
        );

        // 준비 상태가 바뀌었으므로 모든 참가자에게 최신 방 상태 전송
        broadcaster.broadcast(
                context.roomCode(),
                MessageType.ROOM_STATE,
                RoomStateMessage.from(room)
        );

        // TODO:
        // 전원 준비 + 게임 시작 조건이 충족되면
        // GAME_START 방송 및 GameService.startGame() 연결
    }
}