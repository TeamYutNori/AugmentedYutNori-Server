package com.teamyutnori.yutnori.ws.handler;

import com.teamyutnori.yutnori.game.model.GameEndReason;
import com.teamyutnori.yutnori.game.service.GameService;
import com.teamyutnori.yutnori.room.model.Room;
import com.teamyutnori.yutnori.room.service.RoomService;
import com.teamyutnori.yutnori.ws.RoomBroadcaster;
import com.teamyutnori.yutnori.ws.WsMessageContext;
import com.teamyutnori.yutnori.ws.dto.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// LEAVE_ROOM: 대기실 나가기는 그대로, 게임 중이면 removePlayer → 안 되면 기권(FORFEIT) 처리하는지 검증한다
class LeaveRoomWsHandlerTest {

    private static final String ROOM = "ROOM";
    private static final String PLAYER = "p1";

    private GameService gameService;
    private RoomService roomService;
    private RoomBroadcaster broadcaster;
    private LeaveRoomWsHandler handler;
    private final WsMessageContext context = new WsMessageContext(null, ROOM, PLAYER, "닉네임");

    @BeforeEach
    void setUp() {
        gameService = mock(GameService.class);
        roomService = mock(RoomService.class);
        broadcaster = mock(RoomBroadcaster.class);
        handler = new LeaveRoomWsHandler(gameService, roomService, broadcaster);

        // 나간 뒤에도 1명이 남아 있는 방
        Room room = mock(Room.class);
        when(room.getCurrentPlayers()).thenReturn(1);
        when(roomService.leaveRoom(ROOM, PLAYER)).thenReturn(room);
    }

    @Test
    void 게임_중이_아니면_방에서만_나간다() {
        when(gameService.isPlaying(ROOM)).thenReturn(false);

        handler.handle(context, null);

        verify(gameService, never()).removePlayer(anyString(), anyString());
        verify(gameService, never()).forfeit(anyString(), anyInt(), any());
        verify(roomService).leaveRoom(ROOM, PLAYER);
        verify(broadcaster).broadcast(eq(ROOM), eq(MessageType.ROOM_STATE), any());
    }

    @Test
    void 게임_중이고_removePlayer가_처리하면_기권하지_않는다() {
        when(gameService.isPlaying(ROOM)).thenReturn(true);
        when(gameService.removePlayer(ROOM, PLAYER)).thenReturn(true);

        handler.handle(context, null);

        verify(gameService, never()).forfeit(anyString(), anyInt(), any());
        verify(roomService).leaveRoom(ROOM, PLAYER);
    }

    @Test
    void 게임_중이고_removePlayer가_없으면_FORFEIT으로_기권한다() {
        when(gameService.isPlaying(ROOM)).thenReturn(true);
        when(gameService.removePlayer(ROOM, PLAYER)).thenReturn(false);
        when(gameService.findTeam(ROOM, PLAYER)).thenReturn(Optional.of(1));

        handler.handle(context, null);

        verify(gameService).forfeit(ROOM, 1, GameEndReason.FORFEIT);
        verify(roomService).leaveRoom(ROOM, PLAYER);
    }

    @Test
    void 게임_중인데_팀을_못_찾으면_기권하지_않고_방에서만_나간다() {
        when(gameService.isPlaying(ROOM)).thenReturn(true);
        when(gameService.findTeam(ROOM, PLAYER)).thenReturn(Optional.empty());

        handler.handle(context, null);

        verify(gameService, never()).forfeit(anyString(), anyInt(), any());
        verify(roomService).leaveRoom(ROOM, PLAYER);
    }
}