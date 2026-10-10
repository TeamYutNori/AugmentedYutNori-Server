package com.teamyutnori.yutnori.ws.handler;

import com.teamyutnori.yutnori.game.dto.GameRequests.MoveRequest;
import com.teamyutnori.yutnori.game.service.GameService;
import com.teamyutnori.yutnori.ws.WsMessageContext;
import com.teamyutnori.yutnori.ws.dto.MessageType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// MOVE: 말 이동 요청 → GameService.move (검증·판 적용·MOVE_APPLIED·턴 처리)
// 실패하면 GameService가 common 예외를 던지고, MessageRouter가 ERROR로 보내 준다
@Component
@RequiredArgsConstructor
public class MoveWsHandler implements WsMessageHandler {

    private final GameService gameService;
    private final ObjectMapper objectMapper;

    @Override
    public MessageType type() {
        return MessageType.MOVE;
    }

    @Override
    public void handle(WsMessageContext context, JsonNode payload) {
        MoveRequest request = objectMapper.treeToValue(payload, MoveRequest.class);
        gameService.move(context.roomCode(), context.playerId(), request);
    }
}
