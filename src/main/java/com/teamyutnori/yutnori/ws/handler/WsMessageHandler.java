package com.teamyutnori.yutnori.ws.handler;

import com.teamyutnori.yutnori.ws.WsMessageContext;
import com.teamyutnori.yutnori.ws.dto.MessageType;
import tools.jackson.databind.JsonNode;

public interface WsMessageHandler {
    MessageType type();
    void handle(WsMessageContext context, JsonNode payload);
}
