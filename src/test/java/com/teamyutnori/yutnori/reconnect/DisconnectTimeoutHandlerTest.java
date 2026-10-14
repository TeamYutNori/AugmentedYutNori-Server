package com.teamyutnori.yutnori.reconnect;

import com.teamyutnori.yutnori.room.model.Room;
import com.teamyutnori.yutnori.room.model.RoomStatus;
import com.teamyutnori.yutnori.room.service.RoomService;
import com.teamyutnori.yutnori.ws.RoomBroadcaster;
import com.teamyutnori.yutnori.ws.dto.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DisconnectTimeoutHandlerTest {

    private static final String ROOM = "ABC123";

    // 게임 쪽 가짜 구현: 진행 여부, 팀 매핑, 제거·종료 기록만 한다
    static class FakeGame implements GameEndPort {
        boolean playing = true;
        String removedPlayer;
        boolean removeResult = true;

        @Override
        public boolean isPlaying(String roomCode) {
            return playing;
        }

        @Override
        public boolean removePlayer(String roomCode, String playerId) {
            removedPlayer = playerId;
            return removeResult;
        }

        @Override
        public Optional<Integer> findTeam(String roomCode, String playerId) {
            return Optional.empty();
        }

        @Override
        public void endGame(String roomCode, int winnerTeam) {
        }
    }

    private FakeGame game;
    private RoomService roomService;
    private RoomBroadcaster broadcaster;
    private Room room;
    private DisconnectTimeoutHandler handler;

    @BeforeEach
    void setUp() {
        game = new FakeGame();

        roomService = mock(RoomService.class);
        broadcaster = mock(RoomBroadcaster.class);
        room = mock(Room.class);

        when(roomService.getRoomOrThrow(ROOM)).thenReturn(room);
        // 기본 테스트는 게임 중인 상태
        when(room.getStatus()).thenReturn(RoomStatus.PLAYING);

        handler = new DisconnectTimeoutHandler(
                Optional.of(game),
                roomService,
                broadcaster
        );
    }


    // 대기방에서 재접속하지 못하면 방에서 제거한다
    @Test
    void removePlayerFromWaitingRoom() {
        when(room.getStatus()).thenReturn(RoomStatus.WAITING);
        when(room.containsMember("p1")).thenReturn(true);
        when(roomService.leaveRoom(ROOM, "p1")).thenReturn(room);

        handler.onDisconnectTimeout(new DisconnectTimeoutEvent(ROOM, "p1"));

        verify(roomService).leaveRoom(ROOM, "p1");
        verify(broadcaster).broadcast(eq(ROOM), eq(MessageType.ROOM_STATE), any());
        assertThat(game.removedPlayer).isNull();
    }


    // 이미 대기방에서 나간 플레이어면 다시 제거하지 않는다
    @Test
    void alreadyLeftWaitingRoom() {
        when(room.getStatus()).thenReturn(RoomStatus.WAITING);
        when(room.containsMember("p1")).thenReturn(false);

        handler.onDisconnectTimeout(new DisconnectTimeoutEvent(ROOM, "p1"));
        verify(roomService, never()).leaveRoom(anyString(), anyString());
        verifyNoInteractions(broadcaster);
        assertThat(game.removedPlayer).isNull();
    }

    // 게임 중 재접속에 실패하면 게임 참가자에서 제거한다
    @Test
    void removePlayerFromGame() {
        handler.onDisconnectTimeout(new DisconnectTimeoutEvent(ROOM, "p1"));
        assertThat(game.removedPlayer).isEqualTo("p1");
        verifyNoInteractions(broadcaster);
    }

    // 게임 중이 아니면 게임 참가자 제거를 하지 않는다
    @Test
    void notPlaying() {
        game.playing = false;
        handler.onDisconnectTimeout(new DisconnectTimeoutEvent(ROOM, "p1"));
        assertThat(game.removedPlayer).isNull();
        verifyNoInteractions(broadcaster);
    }

    // 게임 참가자가 아니면 추가 처리를 하지 않는다
    @Test
    void playerNotInGame() {
        game.removeResult = false;
        handler.onDisconnectTimeout(new DisconnectTimeoutEvent(ROOM, "p1"));
        assertThat(game.removedPlayer).isEqualTo("p1");
        verifyNoInteractions(broadcaster);
    }

    // GameEndPort 구현이 없어도 대기방 퇴장은 처리한다
    @Test
    void noGamePortButWaitingRoom() {
        when(room.getStatus()).thenReturn(RoomStatus.WAITING);
        when(room.containsMember("p1")).thenReturn(true);
        when(roomService.leaveRoom(ROOM, "p1")).thenReturn(room);

        DisconnectTimeoutHandler noPortHandler = new DisconnectTimeoutHandler(Optional.empty(), roomService, broadcaster);
        noPortHandler.onDisconnectTimeout(new DisconnectTimeoutEvent(ROOM, "p1"));

        verify(roomService).leaveRoom(ROOM, "p1");
        verify(broadcaster).broadcast(eq(ROOM), eq(MessageType.ROOM_STATE), any());
    }
}