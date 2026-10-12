package com.teamyutnori.yutnori.ws.handler;

import com.teamyutnori.yutnori.game.model.GameEndReason;
import com.teamyutnori.yutnori.game.service.GameService;
import com.teamyutnori.yutnori.room.model.Room;
import com.teamyutnori.yutnori.room.service.RoomService;
import com.teamyutnori.yutnori.ws.RoomBroadcaster;
import com.teamyutnori.yutnori.ws.WsMessageContext;
import com.teamyutnori.yutnori.ws.dto.MessageType;
import com.teamyutnori.yutnori.ws.dto.WsMessages.RoomStateMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class LeaveRoomWsHandler implements WsMessageHandler {

    private final GameService gameService;
    private final RoomService roomService;
    private final RoomBroadcaster broadcaster;

    @Override
    public MessageType type() {
        return MessageType.LEAVE_ROOM;
    }

    @Override
    public void handle(
            WsMessageContext context,
            JsonNode payload
    ) {
        // 게임 중 나가기
        if (gameService.isPlaying(context.roomCode())
                && !gameService.removePlayer(context.roomCode(), context.playerId())) {
            // TODO: GameService.removePlayer 구현 후 아래 임시 처리 삭제
            Optional<Integer> team = gameService.findTeam(context.roomCode(), context.playerId());
            if (team.isEmpty()) {
                log.warn("게임 중 나가기: 플레이어의 팀을 찾을 수 없음 room={} player={}",
                        context.roomCode(), context.playerId());
            } else {
                gameService.forfeit(context.roomCode(), team.get(), GameEndReason.FORFEIT);
            }
        }

        // 현재 접속한 플레이어를 방에서 제거
        Room room = roomService.leaveRoom(
                context.roomCode(),
                context.playerId()
        );

        // 방에 다른 참가자가 남아 있다면 변경된 방 상태를 전체에게 전송
        if (room.getCurrentPlayers() > 0) {
            broadcaster.broadcast(
                    context.roomCode(),
                    MessageType.ROOM_STATE,
                    RoomStateMessage.from(room)
            );
        }
    }
}