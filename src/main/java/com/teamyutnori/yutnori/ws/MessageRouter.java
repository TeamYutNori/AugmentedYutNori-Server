package com.teamyutnori.yutnori.ws;

import com.teamyutnori.yutnori.common.BusinessException;
import com.teamyutnori.yutnori.ws.dto.MessageType;
import com.teamyutnori.yutnori.ws.dto.WsMessages.ErrorMessage;
import com.teamyutnori.yutnori.ws.handler.WsMessageHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class MessageRouter {

    private final Map<MessageType, WsMessageHandler> handlers = new HashMap<>();
    private final RoomBroadcaster broadcaster;
    private final ObjectMapper objectMapper;

    public MessageRouter(
            List<WsMessageHandler> handlerList,
            RoomBroadcaster broadcaster,
            ObjectMapper objectMapper) {

        for (WsMessageHandler handler : handlerList) {
            if (handlers.containsKey(handler.type())) {
                throw new IllegalStateException("같은 type의 핸들러가 둘 있습니다: " + handler.type());
            }
            handlers.put(handler.type(), handler);
        }
        this.broadcaster = broadcaster;
        this.objectMapper = objectMapper;
    }

    public void route(WsMessageContext context, String text) {
        JsonNode root;
        try {
            root = objectMapper.readTree(text);
        } catch (JacksonException e) {
            sendError(context, "INVALID_JSON", "JSON 형식이 올바르지 않습니다.");
            return;
        }
        if (!root.isObject()) {
            sendError(context, "INVALID_JSON", "메시지는 JSON 객체여야 합니다.");
            return;
        }

        String typeName = root.path("type").asString("");
        MessageType type;
        try {
            type = MessageType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            type = null;
        }
        WsMessageHandler handler = handlers.get(type);
        if (handler == null) {
            sendError(context, "UNKNOWN_TYPE", "알 수 없는 메시지 종류입니다: " + typeName);
            return;
        }

        JsonNode payload = root.get("payload");
        if (payload == null || payload.isNull()) {
            payload = objectMapper.createObjectNode();
        }

        try {
            handler.handle(context, payload);
        } catch (BusinessException e) {
            sendError(context, e.getCode(), e.getMessage());
        } catch (JacksonException e) {
            sendError(context, "INVALID_REQUEST", "메시지 내용 형식이 올바르지 않습니다.");
        } catch (Exception e) {
            log.error("메시지 처리 중 예상치 못한 오류 type={}", type, e);
            sendError(context, "INTERNAL_ERROR", "서버 오류가 발생했습니다.");
        }
    }

    private void sendError(WsMessageContext context, String code, String message) {
        broadcaster.sendTo(context.roomCode(), context.playerId(), MessageType.ERROR,
                new ErrorMessage(code, message));
    }
}
