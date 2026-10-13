package com.teamyutnori.yutnori.ws.handler;

import com.teamyutnori.yutnori.config.GameProperties;
import com.teamyutnori.yutnori.game.model.GameSetup;
import com.teamyutnori.yutnori.game.service.GameService;
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

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ReadyWsHandler implements WsMessageHandler {

    private final RoomService roomService;
    private final RoomBroadcaster broadcaster;
    private final ObjectMapper objectMapper;
    private final GameService gameService;
    private final GameProperties gameProperties;

    @Override
    public MessageType type() {
        return MessageType.READY;
    }

    @Override
    public void handle(WsMessageContext context, JsonNode payload) {
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

        // 최대 인원이 모두 참가하고 전원이 준비된 첫 요청만 게임 시작 처리
        if (roomService.tryStartGame(context.roomCode())) {
            // GameService에 전달할 playerId → team 정보 생성
            Map<String, Integer> playerTeams =
                    roomService.getPlayerTeams(context.roomCode());

            // 기본 보드와 서버 설정값을 사용해 게임 시작 설정 생성
            GameSetup setup = GameSetup.of(
                    gameProperties,
                    room.getCurrentPlayers(),
                    "default"
            );

            // 모든 참가자에게 게임 시작 알림
            broadcaster.broadcast(
                    context.roomCode(),
                    MessageType.GAME_START,
                    setup
            );

            // 실제 게임 세션 생성 및 시작
            gameService.startGame(
                    context.roomCode(),
                    setup,
                    playerTeams
            );
        }
    }
}