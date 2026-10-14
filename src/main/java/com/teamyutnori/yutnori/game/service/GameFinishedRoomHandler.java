package com.teamyutnori.yutnori.game.service;

import com.teamyutnori.yutnori.room.model.Room;
import com.teamyutnori.yutnori.room.service.RoomService;
import com.teamyutnori.yutnori.ws.RoomBroadcaster;
import com.teamyutnori.yutnori.ws.dto.MessageType;
import com.teamyutnori.yutnori.ws.dto.WsMessages.RoomStateMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GameFinishedRoomHandler {

    private final RoomService roomService;
    private final GameService gameService;
    private final RoomBroadcaster broadcaster;

    // 게임이 종료되어 GameFinishedEvent가 발생하면 호출됨
    @EventListener
    public void on(GameFinishedEvent event) {
        String roomCode = event.roomCode();

        // 게임이 끝난 방을 다시 대기 상태로 변경하고
        // 참가자들의 준비 상태를 false로 초기화
        Room room = roomService.resetAfterGame(roomCode);

        // 끝난 게임 세션을 저장소에서 제거
        // 제거하지 않으면 종료된 게임 세션이 계속 메모리에 남을 수 있음
        gameService.removeGame(roomCode);

        // 변경된 방 상태를 현재 방 참가자들에게 다시 전송
        // 클라이언트는 이 ROOM_STATE를 받아 대기방 화면과 준비 버튼을 갱신
        broadcaster.broadcast(
                roomCode,
                MessageType.ROOM_STATE,
                RoomStateMessage.from(room)
        );
    }
}