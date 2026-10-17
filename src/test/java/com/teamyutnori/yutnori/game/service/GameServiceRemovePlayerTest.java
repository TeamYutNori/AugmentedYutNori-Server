package com.teamyutnori.yutnori.game.service;

import com.teamyutnori.yutnori.config.GameProperties;
import com.teamyutnori.yutnori.game.augment.AugmentDraftService;
import com.teamyutnori.yutnori.game.augment.AugmentEffects;
import com.teamyutnori.yutnori.game.augment.RethrowValidator;
import com.teamyutnori.yutnori.game.board.TestBoards;
import com.teamyutnori.yutnori.game.model.GameEndReason;
import com.teamyutnori.yutnori.game.model.GamePhase;
import com.teamyutnori.yutnori.game.model.GameSession;
import com.teamyutnori.yutnori.game.model.GameSetup;
import com.teamyutnori.yutnori.game.repository.GameSessionRepository;
import com.teamyutnori.yutnori.game.yut.YutThrowService;
import com.teamyutnori.yutnori.reconnect.CommandLog;
import com.teamyutnori.yutnori.reconnect.TurnTimerService;
import com.teamyutnori.yutnori.reward.RewardPolicy;
import com.teamyutnori.yutnori.reward.RewardService;
import com.teamyutnori.yutnori.ws.RoomBroadcaster;
import com.teamyutnori.yutnori.ws.dto.MessageType;
import com.teamyutnori.yutnori.ws.dto.WsMessages.GameEndedMessage;
import com.teamyutnori.yutnori.ws.dto.WsMessages.PlayerRemovedMessage;
import com.teamyutnori.yutnori.ws.dto.WsMessages.RankingEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// 나가기(leaveGame)·재접속 실패(removePlayer): 나간 사람만 빼고 계속, 1명 남으면 종료
// 3팀: p0=0, p1=1, p2=2 / 말: 팀 0 = 0,1 / 팀 1 = 100,101 / 팀 2 = 200,201
class GameServiceRemovePlayerTest {

    private static final String ROOM = "ROOM";

    private GameSession session;
    private RoomBroadcaster broadcaster;
    private RewardService rewardService;
    private ApplicationEventPublisher eventPublisher;
    private GameService service;

    @BeforeEach
    void setUp() {
        session = new GameSession(ROOM, new GameSetup(3, 2, "default", false),
                Map.of("p0", 0, "p1", 1, "p2", 2), TestBoards.defaultBoard()).createPieces();
        session.setPhase(GamePhase.PLAYING);
        session.setCurrentTeam(0);

        GameSessionRepository repository = new GameSessionRepository();
        repository.save(session);
        broadcaster = mock(RoomBroadcaster.class);
        rewardService = mock(RewardService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        service = new GameService(repository, new TurnManager(), mock(YutThrowService.class),
                mock(AugmentDraftService.class), mock(RethrowValidator.class), broadcaster,
                mock(GameProperties.class), mock(TurnTimerService.class), TestBoards.repository(), new CommandLog(),
                mock(AugmentEffects.class), new RewardPolicy(), rewardService, eventPublisher, mock(AutoMovePolicy.class));
    }

    // 다른 팀이 나가면 PLAYER_REMOVED만 보내고 게임은 계속, 그 팀 말은 판에서 사라진다
    @Test
    void removeOtherTeam() {
        session.getBoardState().tryMove(100, 2, 2);

        assertThat(service.removePlayer(ROOM, "p1")).isTrue();

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(broadcaster).broadcast(eq(ROOM), eq(MessageType.PLAYER_REMOVED), captor.capture());
        PlayerRemovedMessage removed = (PlayerRemovedMessage) captor.getValue();
        assertThat(removed.playerId()).isEqualTo("p1");
        assertThat(removed.team()).isEqualTo(1);
        assertThat(removed.reason()).isEqualTo(GameEndReason.DISCONNECTED);

        assertThat(session.isRemoved(1)).isTrue();
        assertThat(session.getBoardState().getPiece(100)).isNull();
        assertThat(session.getPhase()).isEqualTo(GamePhase.PLAYING);
        assertThat(session.getCurrentTeam()).isZero();
        verify(broadcaster, never()).broadcast(eq(ROOM), eq(MessageType.TURN_CHANGED), any());
        verify(broadcaster, never()).broadcast(eq(ROOM), eq(MessageType.GAME_ENDED), any());
    }

    // 지금 턴인 팀이 나가면 다음 팀으로 넘어간다
    @Test
    void removeCurrentTeamPassesTurn() {
        service.leaveGame(ROOM, "p0");

        InOrder inOrder = inOrder(broadcaster);
        inOrder.verify(broadcaster).broadcast(eq(ROOM), eq(MessageType.PLAYER_REMOVED), any());
        inOrder.verify(broadcaster).broadcast(eq(ROOM), eq(MessageType.TURN_CHANGED), any());
        assertThat(session.getCurrentTeam()).isEqualTo(1);
    }

    // 나간 팀은 턴 순서에서 빠진다 (1 → 0, 2 건너뜀)
    @Test
    void removedTeamIsSkipped() {
        service.leaveGame(ROOM, "p2");
        session.setCurrentTeam(1);

        new TurnManager().nextTurn(session);

        assertThat(session.getCurrentTeam()).isZero();
    }

    // 1명만 남으면 PLAYER_REMOVED → GAME_ENDED 순서로 보내고, 남은 사람이 1등
    // 기준 턴 뒤에 나갔으니 보상 인원은 3명 그대로 → 3인 표 1등 400
    @Test
    void lastOneStandingEndsGame() {
        session.setTurnNumber(5);
        service.leaveGame(ROOM, "p1");
        service.removePlayer(ROOM, "p2");

        InOrder inOrder = inOrder(broadcaster);
        inOrder.verify(broadcaster, times(2)).broadcast(eq(ROOM), eq(MessageType.PLAYER_REMOVED), any());
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        inOrder.verify(broadcaster).broadcast(eq(ROOM), eq(MessageType.GAME_ENDED), captor.capture());

        GameEndedMessage ended = (GameEndedMessage) captor.getValue();
        assertThat(ended.winnerTeam()).isZero();
        assertThat(ended.reason()).isEqualTo(GameEndReason.DISCONNECTED);
        assertThat(ended.rankings()).extracting(RankingEntry::playerId).containsExactly("p0", "p1", "p2");
        assertThat(ended.rankings()).extracting(RankingEntry::rank).containsExactly(1, 2, 2);
        assertThat(ended.rankings()).extracting(RankingEntry::rewardGold).containsExactly(400, 0, 0);
        assertThat(session.getPhase()).isEqualTo(GamePhase.ENDED);
    }

    // 기준 턴 안에 둘 다 나가면 보상 인원 1명 → 남은 사람도 0골드 (파밍 방지)
    @Test
    void everyoneLeftEarlyGivesNoReward() {
        session.setTurnNumber(1);
        service.leaveGame(ROOM, "p1");
        service.removePlayer(ROOM, "p2");

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(broadcaster).broadcast(eq(ROOM), eq(MessageType.GAME_ENDED), captor.capture());
        GameEndedMessage ended = (GameEndedMessage) captor.getValue();
        assertThat(ended.rankings()).extracting(RankingEntry::rank).containsExactly(1, 2, 2);
        assertThat(ended.rankings()).extracting(RankingEntry::rewardGold).containsExactly(0, 0, 0);
        assertThat(ended.rankings()).extracting(RankingEntry::leftGame).containsExactly(false, true, true);   // 남은 1등은 0골드여도 나간 게 아님
    }

    // 나가기와 끊김(30초 후 제거) 모두 나간 턴을 기록한다
    @Test
    void leaveAndDisconnectRecordRemovedTurn() {
        session.setTurnNumber(3);
        service.leaveGame(ROOM, "p1");
        session.setTurnNumber(4);
        service.removePlayer(ROOM, "p2");

        assertThat(session.removedAtTurn(1)).hasValue(3);
        assertThat(session.removedAtTurn(2)).hasValue(4);
        assertThat(session.removedAtTurn(0)).isEmpty();
    }

    // 종료 시 rankings 그대로 골드 지급 + 종료 이벤트 발행
    @Test
    void endGrantsRewardAndPublishesEvent() {
        service.leaveGame(ROOM, "p1");
        service.leaveGame(ROOM, "p2");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RankingEntry>> rankings = ArgumentCaptor.forClass(List.class);
        verify(rewardService).grant(rankings.capture());
        assertThat(rankings.getValue()).hasSize(3);

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(event.capture());
        GameFinishedEvent finished = (GameFinishedEvent) event.getValue();
        assertThat(finished.roomCode()).isEqualTo(ROOM);
        assertThat(finished.winnerTeam()).isZero();
        assertThat(finished.rankings()).isEqualTo(rankings.getValue());
    }

    // 골드 지급이 실패해도 GAME_ENDED는 나간다
    @Test
    void rewardFailureStillEnds() {
        when(rewardService.grant(any())).thenThrow(new RuntimeException("db down"));

        service.leaveGame(ROOM, "p1");
        service.leaveGame(ROOM, "p2");

        verify(broadcaster).broadcast(eq(ROOM), eq(MessageType.GAME_ENDED), any());
    }

    // 같은 사람이 두 번 빠져도(나가기 후 끊김 등) 한 번만 처리
    @Test
    void removeTwiceOnce() {
        service.leaveGame(ROOM, "p1");
        assertThat(service.removePlayer(ROOM, "p1")).isTrue();

        verify(broadcaster, times(1)).broadcast(eq(ROOM), eq(MessageType.PLAYER_REMOVED), any());
    }

    // 이 게임 참가자가 아니거나 게임이 없으면 false
    @Test
    void unknownPlayerOrRoom() {
        assertThat(service.removePlayer(ROOM, "ghost")).isFalse();
        assertThat(service.removePlayer("NONE", "p0")).isFalse();
        verifyNoInteractions(broadcaster);
    }

    // 증강 선택 중에 아직 안 고른 팀이 나가고, 남은 팀은 다 골랐으면 바로 게임 시작
    @Test
    void removeDuringAugmentSelectStartsGame() {
        session.setPhase(GamePhase.AUGMENT_SELECT);
        session.setOffered(2, List.of("Sprinter", "Monk"));

        service.leaveGame(ROOM, "p2");

        assertThat(session.getPhase()).isEqualTo(GamePhase.PLAYING);
        verify(broadcaster).broadcast(eq(ROOM), eq(MessageType.TURN_CHANGED), any());
    }
}
