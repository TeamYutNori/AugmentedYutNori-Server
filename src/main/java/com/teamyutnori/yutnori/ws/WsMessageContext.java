package com.teamyutnori.yutnori.ws;

import org.springframework.web.socket.WebSocketSession;

public record WsMessageContext(
        WebSocketSession session,
        String roomCode,
        String playerId,
        String nickname
) {
    public static final String ATTR_ROOM_CODE = "roomCode";
    public static final String ATTR_PLAYER_ID = "playerId";
    public static final String ATTR_NICKNAME = "nickname";

    static WsMessageContext from(WebSocketSession session) {
        String roomCode = (String)session.getAttributes().get(ATTR_ROOM_CODE);
        String playerId = (String)session.getAttributes().get(ATTR_PLAYER_ID);
        String nickname = (String)session.getAttributes().get(ATTR_NICKNAME);

        return new WsMessageContext(session, roomCode, playerId, nickname);
    }
}
