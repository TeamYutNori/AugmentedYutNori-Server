package com.teamyutnori.yutnori.ws.handler;

import com.teamyutnori.yutnori.game.dto.GameRequests.MoveRequest;
import com.teamyutnori.yutnori.game.service.MoveRelayService;
import com.teamyutnori.yutnori.ws.WsMessageContext;
import com.teamyutnori.yutnori.ws.dto.MessageType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class MoveWsHandler implements WsMessageHandler{

    private final MoveRelayService moveRelayService;
    private final ObjectMapper objectMapper;

    @Override
    public MessageType type(){
        return MessageType.MOVE;
    }

    @Override
    public void handle(WsMessageContext context, JsonNode payload){
        MoveRequest request = objectMapper.treeToValue(payload, MoveRequest.class);
        moveRelayService.move(context.roomCode(), context.playerId(), request);
    }
}
