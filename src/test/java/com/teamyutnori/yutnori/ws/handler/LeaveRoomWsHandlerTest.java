package com.teamyutnori.yutnori.ws.handler;

import com.teamyutnori.yutnori.game.service.GameService;
import com.teamyutnori.yutnori.room.model.Room;
import com.teamyutnori.yutnori.room.service.RoomService;
import com.teamyutnori.yutnori.ws.RoomBroadcaster;
import com.teamyutnori.yutnori.ws.WsMessageContext;
import com.teamyutnori.yutnori.ws.dto.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// LEAVE_ROOM: 대기실 나가기는 그대로, 게임 중이면 leaveGame(FORFEIT)으로 게임에서도 빼는지 검증한다
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

        verify(gameService, never()).leaveGame(anyString(), anyString());
        verify(roomService).leaveRoom(ROOM, PLAYER);
        verify(broadcaster).broadcast(eq(ROOM), eq(MessageType.ROOM_STATE), any());
    }

    @Test
    void 게임_중이면_게임에서_빼고_방에서도_나간다() {
        when(gameService.isPlaying(ROOM)).thenReturn(true);

        handler.handle(context, null);

        InOrder inOrder = inOrder(gameService, roomService);
        inOrder.verify(gameService).leaveGame(ROOM, PLAYER);
        inOrder.verify(roomService).leaveRoom(ROOM, PLAYER);
        verify(broadcaster).broadcast(eq(ROOM), eq(MessageType.ROOM_STATE), any());
    }
}