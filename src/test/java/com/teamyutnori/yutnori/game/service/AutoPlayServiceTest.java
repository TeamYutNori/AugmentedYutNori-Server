package com.teamyutnori.yutnori.game.service;

import com.teamyutnori.yutnori.config.GameProperties;
import com.teamyutnori.yutnori.game.augment.AugmentDraftService;
import com.teamyutnori.yutnori.game.augment.AugmentEffects;
import com.teamyutnori.yutnori.game.augment.RethrowValidator;
import com.teamyutnori.yutnori.game.board.TestBoards;
import com.teamyutnori.yutnori.game.dto.GameMessages.MoveAppliedMessage;
import com.teamyutnori.yutnori.game.dto.GameMessages.TurnChangedMessage;
import com.teamyutnori.yutnori.game.model.GamePhase;
import com.teamyutnori.yutnori.game.model.GameSession;
import com.teamyutnori.yutnori.game.model.GameSetup;
import com.teamyutnori.yutnori.game.repository.GameSessionRepository;
import com.teamyutnori.yutnori.game.yut.YutResult;
import com.teamyutnori.yutnori.game.yut.YutThrowOutcome;
import com.teamyutnori.yutnori.game.yut.YutThrowService;
import com.teamyutnori.yutnori.reconnect.CommandLog;
import com.teamyutnori.yutnori.reconnect.PlayerDisconnectedEvent;
import com.teamyutnori.yutnori.reconnect.PlayerReconnectedEvent;
import com.teamyutnori.yutnori.reconnect.TurnTimerService;
import com.teamyutnori.yutnori.reward.RewardPolicy;
import com.teamyutnori.yutnori.reward.RewardService;
import com.teamyutnori.yutnori.ws.RoomBroadcaster;
import com.teamyutnori.yutnori.ws.dto.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// 끊긴 플레이어 자동 진행: 끊김 → 자동 표시, 제한시간 끝나면 대신 던지기·이동, 재접속 → 수동
// 2팀: p0 = 0, p1 = 1 / 말: 팀 0 = 0, 1 / 팀 1 = 100, 101
class AutoPlayServiceTest {

    private static final String ROOM = "ROOM";

    private GameSession session;
    private RoomBroadcaster broadcaster;
    private YutThrowService yutThrowService;
    private GameService gameService;
    private AutoPlayService autoPlay;

    private final List<MessageType> sent = new ArrayList<>();
    private final List<Object> payloads = new ArrayList<>();
    private final Deque<Runnable> scheduled = new ArrayDeque<>();

    @BeforeEach
    void setUp() {
        session = new GameSession(ROOM, new GameSetup(2, 2, "default", false),
                Map.of("p0", 0, "p1", 1), TestBoards.defaultBoard()).createPieces();
        session.setPhase(GamePhase.PLAYING);
        session.setCurrentTeam(0);
        session.setRemainingThrows(1);

        GameSessionRepository repository = new GameSessionRepository();
        repository.save(session);

        broadcaster = mock(RoomBroadcaster.class);
        when(broadcaster.broadcast(any(), any(), any())).thenAnswer(inv -> {
            sent.add(inv.getArgument(1));
            payloads.add(inv.getArgument(2));
            return (long) sent.size();
        });
        yutThrowService = mock(YutThrowService.class);

        // GameService가 발행한 AutoTurnEvent를 AutoPlayService로 바로 넘긴다 (스프링 이벤트 대신)
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        doAnswer(inv -> {
            if (inv.getArgument(0) instanceof AutoTurnEvent event) autoPlay.onAutoTurn(event);
            return null;
        }).when(publisher).publishEvent(any(Object.class));

        gameService = new GameService(repository, new TurnManager(), yutThrowService,
                mock(AugmentDraftService.class), mock(RethrowValidator.class), broadcaster,
                mock(GameProperties.class), mock(TurnTimerService.class), TestBoards.repository(), new CommandLog(),
                mock(AugmentEffects.class), new RewardPolicy(), mock(RewardService.class), publisher,  new AutoMovePolicy());

        // 예약된 다음 단계는 큐에 모아 두고 테스트가 직접 실행한다
        TaskScheduler scheduler = mock(TaskScheduler.class);
        when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(inv -> {
            scheduled.add(inv.getArgument(0));
            return null;
        });
        autoPlay = new AutoPlayService(repository, gameService, new AutoMovePolicy(), scheduler,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), Duration.ofMillis(800));
    }

    private void throwsWill(YutResult... results) {
        var stub = when(yutThrowService.throwYut());
        for (YutResult r : results) stub = stub.thenReturn(new YutThrowOutcome(new boolean[4], r));
    }

    private void runScheduled() {
        while (!scheduled.isEmpty()) scheduled.poll().run();
    }

    @Test
    void 끊기면_자동_재접속하면_수동() {
        autoPlay.onDisconnected(new PlayerDisconnectedEvent(ROOM, "p0"));
        assertThat(session.isAuto(0)).isTrue();
        assertThat(session.isAuto(1)).isFalse();

        autoPlay.onReconnected(new PlayerReconnectedEvent(ROOM, "p0"));
        assertThat(session.isAuto(0)).isFalse();
    }

    // 연결된 팀도 시간 초과면 한 단계 자동 진행 (윷 던지기)
    // 이동할 게 생기면 같은 팀에 새 제한시간 (TURN_CHANGED 다시). 자동 진행(AutoPlay)은 돌지 않는다
    @Test
    void 연결된_팀은_시간초과면_자동으로_던진다() {
        throwsWill(YutResult.Gae);

        gameService.onTurnTimeout(ROOM, 0);

        assertThat(sent).containsExactly(MessageType.THROW_RESULT);   // 같은 턴 TURN_CHANGED 재전송 없음
        assertThat(session.getCurrentTeam()).isZero();
        assertThat(scheduled).isEmpty();   // AutoPlayService의 단계 예약 없음
    }

    @Test
    void 끊긴_팀은_시간초과면_대신_던지고_옮긴다() {
        throwsWill(YutResult.Gae);
        autoPlay.onDisconnected(new PlayerDisconnectedEvent(ROOM, "p0"));

        gameService.onTurnTimeout(ROOM, 0);
        assertThat(sent).containsExactly(MessageType.THROW_RESULT);   // 첫 단계는 바로, 다음은 예약

        runScheduled();

        assertThat(sent).containsExactly(MessageType.THROW_RESULT, MessageType.MOVE_APPLIED, MessageType.TURN_CHANGED);
        MoveAppliedMessage applied = (MoveAppliedMessage) payloads.get(1);
        assertThat(applied.auto()).isTrue();
        assertThat(applied.stateHash()).isNull();
        assertThat(applied.pieceId()).isZero();
        assertThat(applied.destinationNodeId()).isEqualTo(2);
        assertThat(session.getCurrentTeam()).isEqualTo(1);
    }

    @Test
    void 윷이면_다_던진_뒤에_옮긴다() {
        throwsWill(YutResult.Yut, YutResult.Do);
        autoPlay.onDisconnected(new PlayerDisconnectedEvent(ROOM, "p0"));

        gameService.onTurnTimeout(ROOM, 0);
        runScheduled();

        assertThat(sent).startsWith(MessageType.THROW_RESULT, MessageType.THROW_RESULT,
                MessageType.MOVE_APPLIED, MessageType.MOVE_APPLIED);
        assertThat(sent.get(sent.size() - 1)).isEqualTo(MessageType.TURN_CHANGED);
        assertThat(session.getCurrentTeam()).isEqualTo(1);
    }

    @Test
    void 재접속하면_남은_행동은_본인에게_돌려준다() {
        throwsWill(YutResult.Gae);
        autoPlay.onDisconnected(new PlayerDisconnectedEvent(ROOM, "p0"));
        gameService.onTurnTimeout(ROOM, 0);   // 던지기까지 진행

        autoPlay.onReconnected(new PlayerReconnectedEvent(ROOM, "p0"));
        runScheduled();

        assertThat(sent).containsExactly(MessageType.THROW_RESULT, MessageType.TURN_CHANGED);
        TurnChangedMessage turn = (TurnChangedMessage) payloads.get(1);
        assertThat(turn.team()).isZero();                  // 같은 팀에게 다시 알림 (타이머 새로)
        assertThat(session.getStoredResults()).hasSize(1); // 이동은 본인이
    }

    @Test
    void 자동_이동으로_다_들어오면_게임_종료() {
        // 팀 0의 말 1은 이미 골인, 말 0은 19번 → 개로 골인
        var board = session.getBoardState();
        board.tryMove(1, 4, 4); board.tryMove(1, 5, 9); board.tryMove(1, 5, 14); board.tryMove(1, 5, 19); board.tryMove(1, 2, 0);
        board.tryMove(0, 4, 4); board.tryMove(0, 5, 9); board.tryMove(0, 5, 14); board.tryMove(0, 5, 19);
        throwsWill(YutResult.Gae);
        autoPlay.onDisconnected(new PlayerDisconnectedEvent(ROOM, "p0"));

        gameService.onTurnTimeout(ROOM, 0);
        runScheduled();

        assertThat(sent).containsExactly(MessageType.THROW_RESULT, MessageType.MOVE_APPLIED, MessageType.GAME_ENDED);
        assertThat(session.getPhase()).isEqualTo(GamePhase.ENDED);
        assertThat(session.getWinnerTeam()).isZero();
    }

    @Test
    void 나간_팀은_자동_진행하지_않는다() {
        throwsWill(YutResult.Yut, YutResult.Gae);
        autoPlay.onDisconnected(new PlayerDisconnectedEvent(ROOM, "p0"));
        gameService.onTurnTimeout(ROOM, 0);   // 윷 → 한 번 더 예약됨

        gameService.removePlayer(ROOM, "p0"); // 유예시간 초과로 제거 → 게임 종료
        sent.clear();
        runScheduled();

        assertThat(sent).isEmpty();
    }
}
