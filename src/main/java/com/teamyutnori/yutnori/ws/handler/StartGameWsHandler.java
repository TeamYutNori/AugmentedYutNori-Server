package com.teamyutnori.yutnori.ws.handler;

import com.teamyutnori.yutnori.config.GameProperties;
import com.teamyutnori.yutnori.game.model.GameSetup;
import com.teamyutnori.yutnori.game.service.GameService;
import com.teamyutnori.yutnori.room.model.Room;
import com.teamyutnori.yutnori.room.service.RoomService;
import com.teamyutnori.yutnori.ws.RoomBroadcaster;
import com.teamyutnori.yutnori.ws.RoomSessionRegistry;
import com.teamyutnori.yutnori.ws.WsMessageContext;
import com.teamyutnori.yutnori.ws.dto.MessageType;
import com.teamyutnori.yutnori.ws.dto.WsMessages.RoomStateMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class StartGameWsHandler implements WsMessageHandler {

    private final RoomService roomService;
    private final RoomBroadcaster broadcaster;
    private final RoomSessionRegistry registry;
    private final GameService gameService;
    private final GameProperties gameProperties;

    @Override
    public MessageType type() {
        return MessageType.START_GAME;
    }

    @Override
    public void handle(
            WsMessageContext context,
            JsonNode payload
    ) {
        String roomCode = context.roomCode();

        // 방장 여부, 방 상태, 인원, 준비 상태, 연결 상태를 확인하고
        // 팀 번호 정리 후 방을 PLAYING 상태로 변경
        Room room = roomService.requestGameStart(
                roomCode,
                context.playerId(),
                playerId ->
                        registry.find(roomCode, playerId)
                                .map(session -> session.isOpen())
                                .orElse(false)
        );

        // 팀 번호가 바뀌었을 수 있으므로
        // GAME_START보다 먼저 최신 ROOM_STATE를 전달
        broadcaster.broadcast(
                roomCode,
                MessageType.ROOM_STATE,
                RoomStateMessage.from(room)
        );

        // 정리된 playerId → team 정보 생성
        Map<String, Integer> playerTeams =
                roomService.getPlayerTeams(roomCode);

        // 실제 참가 인원 기준으로 GameSetup 생성
        GameSetup setup = GameSetup.of(
                gameProperties,
                room.getCurrentPlayers(),
                "default"
        );

        // 클라이언트에게 게임 시작 알림
        broadcaster.broadcast(
                roomCode,
                MessageType.GAME_START,
                setup
        );

        // 실제 게임 세션 생성
        gameService.startGame(
                roomCode,
                setup,
                playerTeams
        );
    }
}