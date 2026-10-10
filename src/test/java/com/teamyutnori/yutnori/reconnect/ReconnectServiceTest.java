package com.teamyutnori.yutnori.reconnect;

import com.teamyutnori.yutnori.game.model.GamePhase;
import com.teamyutnori.yutnori.game.board.TestBoards;
import com.teamyutnori.yutnori.game.model.GameSession;
import com.teamyutnori.yutnori.game.model.GameSetup;
import com.teamyutnori.yutnori.game.repository.GameSessionRepository;
import com.teamyutnori.yutnori.game.service.StateDesyncEvent;
import com.teamyutnori.yutnori.game.yut.YutResult;
import com.teamyutnori.yutnori.ws.RoomBroadcaster;
import com.teamyutnori.yutnori.ws.SequenceTracker;
import com.teamyutnori.yutnori.ws.dto.MessageType;
import com.teamyutnori.yutnori.ws.dto.WsMessages.GameSnapshotMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReconnectServiceTest {

    private static final String ROOM = "ROOM";

    private GameSession session;
    private GameSessionRepository repository;
    private RoomBroadcaster broadcaster;
    private TurnTimerService turnTimerService;
    private SequenceTracker sequenceTracker;
    private ReconnectService service;

    @BeforeEach
    void setUp() {
        // 팀 1 차례, 걸·개 결과 보유, 팀 0은 SturdyCarry 보유
        session = new GameSession(ROOM, new GameSetup(2, 2, "default", false), Map.of("p0", 0, "p1", 1), TestBoards.defaultBoard());
        session.setPhase(GamePhase.PLAYING);
        session.setCurrentTeam(1);
        session.setRemainingThrows(1);
        session.setTurnNumber(4);
        session.getStoredResults().addAll(List.of(YutResult.Geol, YutResult.Gae));
        session.addAugment(0, "SturdyCarry");

        repository = mock(GameSessionRepository.class);
        when(repository.find(ROOM)).thenReturn(Optional.of(session));
        broadcaster = mock(RoomBroadcaster.class);
        turnTimerService = mock(TurnTimerService.class);
        when(turnTimerService.currentType(ROOM)).thenReturn(Optional.of(TimerType.TURN));
        when(turnTimerService.remaining(ROOM)).thenReturn(Optional.of(Duration.ofMillis(12_300)));
        sequenceTracker = new SequenceTracker();
        sequenceTracker.next(ROOM);
        sequenceTracker.next(ROOM);   // 지금까지 seq 2까지 보냄

        service = new ReconnectService(repository, broadcaster, turnTimerService, sequenceTracker);
    }

    private GameSnapshotMessage captureSentTo(String playerId) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(broadcaster).sendTo(eq(ROOM), eq(playerId), eq(MessageType.GAME_SNAPSHOT), captor.capture());
        return (GameSnapshotMessage) captor.getValue();
    }

    // 재접속하면 돌아온 플레이어에게만 현재 상태를 보낸다
    @Test
    void reconnect() {
        service.onReconnected(new PlayerReconnectedEvent(ROOM, "p0"));

        GameSnapshotMessage snapshot = captureSentTo("p0");
        assertThat(snapshot.lastSeq()).isEqualTo(2);
        assertThat(snapshot.phase()).isEqualTo(GamePhase.PLAYING);
        assertThat(snapshot.myTeam()).isZero();
        assertThat(snapshot.currentTeam()).isEqualTo(1);
        assertThat(snapshot.remainingThrows()).isEqualTo(1);
        assertThat(snapshot.turnNumber()).isEqualTo(4);
        assertThat(snapshot.storedResults()).containsExactly(YutResult.Geol, YutResult.Gae);
        assertThat(snapshot.ownedAugments().get(0)).containsExactly("SturdyCarry");
        assertThat(snapshot.winnerTeam()).isNull();
        verify(broadcaster, never()).broadcast(any(), any(), any());
    }

    // 타이머 종류와 남은 시간(올림한 초)이 담긴다
    @Test
    void timer() {
        service.sendSnapshot(ROOM, "p0");

        GameSnapshotMessage snapshot = captureSentTo("p0");
        assertThat(snapshot.timerType()).isEqualTo(TimerType.TURN);
        assertThat(snapshot.timerRemainingSec()).isEqualTo(13);
    }

    // 타이머가 없으면 null / 0
    @Test
    void noTimer() {
        when(turnTimerService.currentType(ROOM)).thenReturn(Optional.empty());
        when(turnTimerService.remaining(ROOM)).thenReturn(Optional.empty());

        service.sendSnapshot(ROOM, "p0");

        GameSnapshotMessage snapshot = captureSentTo("p0");
        assertThat(snapshot.timerType()).isNull();
        assertThat(snapshot.timerRemainingSec()).isZero();
    }

    // 증강 선택 중이면 내 팀 후보만 담긴다 (상대 후보는 보이지 않음)
    @Test
    void offeredOnlyMine() {
        session.setPhase(GamePhase.AUGMENT_SELECT);
        session.setOffered(0, List.of("Rethrow", "QuickStart"));
        session.setOffered(1, List.of("WeakBoost", "SturdyCarry", "BackDoSwitch"));

        service.sendSnapshot(ROOM, "p1");

        GameSnapshotMessage snapshot = captureSentTo("p1");
        assertThat(snapshot.offeredAugments()).containsExactly("WeakBoost", "SturdyCarry", "BackDoSwitch");
    }

    // DESYNC가 나면 방의 모든 플레이어에게 보낸다
    @Test
    void desync() {
        service.onDesync(new StateDesyncEvent(ROOM, 2, Map.of("p0", "a", "p1", "b")));

        verify(broadcaster).sendTo(eq(ROOM), eq("p0"), eq(MessageType.GAME_SNAPSHOT), any());
        verify(broadcaster).sendTo(eq(ROOM), eq("p1"), eq(MessageType.GAME_SNAPSHOT), any());
    }

    // 게임이 없는 방이면 보내지 않고 false
    @Test
    void noGame() {
        when(repository.find("LOBBY")).thenReturn(Optional.empty());

        assertThat(service.sendSnapshot("LOBBY", "p0")).isFalse();
        verifyNoInteractions(broadcaster);
    }

    // 게임 참가자가 아니면 보내지 않고 false
    @Test
    void notInGame() {
        assertThat(service.sendSnapshot(ROOM, "ghost")).isFalse();
        verifyNoInteractions(broadcaster);
    }
}