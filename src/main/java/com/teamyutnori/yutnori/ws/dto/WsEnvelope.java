package com.teamyutnori.yutnori.ws.dto;

import tools.jackson.databind.JsonNode;

// 모든 WebSocket 메시지의 공통 겉모양 { type, seq, payload }. seq는 서버→클라 순번(클라가 보낼 땐 0)
public record WsEnvelope(MessageType type, long seq, JsonNode payload) {}
