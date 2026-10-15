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
import com.teamyutnori.yutnori.reconnect.TimerType;
import com.teamyutnori.yutnori.reconnect.TurnTimerService;
import com.teamyutnori.yutnori.reward.RewardPolicy;
import com.teamyutnori.yutnori.reward.RewardService;
import com.teamyutnori.yutnori.ws.RoomBroadcaster;
import com.teamyutnori.yutnori.ws.dto.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// 시간 초과 처리
//  - 턴: 연결된 팀은 한 단계 자동 (던질 게 있으면 던지기, 없으면 자동 이동), 턴이 이어지면 TURN_CHANGED 다시
//        끊긴 팀은 AutoTurnEvent만 발행 (AutoPlayService가 처리)
//  - 증강 선택: 안 고른 팀은 후보 중 랜덤
// 2팀: p0 = 0, p1 = 1 / 말: 팀 0 = 0, 1 / 팀 1 = 100, 101
class GameServiceTimeoutTest {

    private static final String ROOM = "ROOM";

    private GameSession session;
    private RoomBroadcaster broadcaster;
    private YutThrowService yutThrowService;
    private ApplicationEventPublisher publisher;
    private GameService gameService;

    private final List<MessageType> sent = new ArrayList<>();
    private final List<Object> payloads = new ArrayList<>();
    private TurnTimerService turnTimerService;

    @BeforeEach
    void setUp() {
        // 기본: 팀 0 차례, 남은 던지기 1, 판에 말 없음
        session = new GameSession(ROOM, new GameSetup(2, 2, "default", false),
                Map.of("p0", 0, "p1", 1), TestBoards.defaultBoard()).createPieces();
        session.setPhase(GamePhase.PLAYING);
        session.setCurrentTeam(0);
        session.setRemainingThrows(1);

        GameSessionRepository repository = new GameSessionRepository();
        repository.save(session);
        turnTimerService = mock(TurnTimerService.class);

        broadcaster = mock(RoomBroadcaster.class);
        when(broadcaster.broadcast(any(), any(), any())).thenAnswer(inv -> {
            sent.add(inv.getArgument(1));
            payloads.add(inv.getArgument(2));
            return (long) sent.size();
        });

        yutThrowService = mock(YutThrowService.class);
        publisher = mock(ApplicationEventPublisher.class);

        gameService = new GameService(repository, new TurnManager(), yutThrowService,
                mock(AugmentDraftService.class), mock(RethrowValidator.class), broadcaster,
                mock(GameProperties.class), turnTimerService, TestBoards.repository(), new CommandLog(),
                mock(AugmentEffects.class), new RewardPolicy(), mock(RewardService.class), publisher,
                new AutoMovePolicy());
    }

    private void throwsWill(YutResult result) {
        when(yutThrowService.throwYut()).thenReturn(new YutThrowOutcome(new boolean[4], result));
    }

    private <T> T last(MessageType type, Class<T> payloadType) {
        for (int i = sent.size() - 1; i >= 0; i--) {
            if (sent.get(i) == type) return payloadType.cast(payloads.get(i));
        }
        throw new AssertionError(type + " 메시지가 없습니다. 보낸 메시지: " + sent);
    }

    // ── 턴 시간 초과 ──

    // 판에 말이 없는데 빽도면 할 게 없으므로 다음 팀 (TURN_CHANGED는 한 번만)
    @Test
    void throwNoAction() {
        throwsWill(YutResult.BackDo);

        gameService.onTurnTimeout(ROOM, 0);

        assertThat(sent).containsExactly(MessageType.THROW_RESULT, MessageType.TURN_CHANGED);
        assertThat(last(MessageType.TURN_CHANGED, TurnChangedMessage.class).team()).isEqualTo(1);
        assertThat(session.getCurrentTeam()).isEqualTo(1);
    }

    // 던질 게 없고 이동만 남았으면 자동 이동 (MOVE_APPLIED auto = true), 결과를 다 쓰면 다음 팀
    @Test
    void autoMove() {
        session.setRemainingThrows(0);
        session.getStoredResults().add(YutResult.Gae);

        gameService.onTurnTimeout(ROOM, 0);

        MoveAppliedMessage move = last(MessageType.MOVE_APPLIED, MoveAppliedMessage.class);
        assertThat(move.auto()).isTrue();
        assertThat(move.team()).isZero();
        assertThat(session.getStoredResults()).isEmpty();
        assertThat(session.getCurrentTeam()).isEqualTo(1);
        verifyNoInteractions(yutThrowService);
    }

    // 끊긴 팀(자동 표시)은 직접 두지 않고 AutoTurnEvent만 발행
    @Test
    void autoTeam() {
        session.setAuto(0, true);

        gameService.onTurnTimeout(ROOM, 0);

        verify(publisher).publishEvent(any(AutoTurnEvent.class));
        assertThat(sent).isEmpty();
        verifyNoInteractions(yutThrowService);
    }

    // 그 사이 턴이 바뀌었으면 (늦게 온 타이머) 무시
    @Test
    void staleTeam() {
        gameService.onTurnTimeout(ROOM, 1);

        assertThat(sent).isEmpty();
        verifyNoInteractions(yutThrowService);
    }

    // 게임 중이 아니면 무시
    @Test
    void notPlaying() {
        session.setPhase(GamePhase.ENDED);

        gameService.onTurnTimeout(ROOM, 0);

        assertThat(sent).isEmpty();
    }

    // ── 증강 선택 시간 초과 ──

    // 안 고른 팀은 후보 중 랜덤으로 선택 (랜덤을 "마지막 후보"로 고정해서 확인)
    @Test
    void augmentRandom() {
        session.setPhase(GamePhase.AUGMENT_SELECT);
        session.setOffered(0, List.of("Monk", "Sprinter", "Contrarian"));
        session.setOffered(1, List.of("Hunter", "Gambler", "Loner"));
        gameService.setRandom(new Random() {
            @Override
            public int nextInt(int bound) { return bound - 1; }
        });

        gameService.onAugmentSelectTimeout(ROOM);

        assertThat(session.getOwnedAugments().get(0)).containsExactly("Contrarian");
        assertThat(session.getOwnedAugments().get(1)).containsExactly("Loner");
        assertThat(sent).contains(MessageType.AUGMENT_SELECTED);
        assertThat(session.getPhase()).isEqualTo(GamePhase.PLAYING);   // 둘 다 골랐으니 시작
    }

    // 이미 고른 팀은 건드리지 않는다 (안 고른 팀만 랜덤)
    @Test
    void augmentOnlyPending() {
        session.setPhase(GamePhase.AUGMENT_SELECT);
        session.addAugment(0, "Monk");                         // 팀 0은 이미 고름 (후보 없음)
        session.setOffered(1, List.of("Hunter", "Gambler"));
        gameService.setRandom(new Random() {
            @Override
            public int nextInt(int bound) { return 0; }
        });

        gameService.onAugmentSelectTimeout(ROOM);

        assertThat(session.getOwnedAugments().get(0)).containsExactly("Monk");
        assertThat(session.getOwnedAugments().get(1)).containsExactly("Hunter");
    }

    // 증강 선택 단계가 아니면 무시
    @Test
    void augmentWrongPhase() {
        gameService.onAugmentSelectTimeout(ROOM);   // 기본은 PLAYING

        assertThat(sent).isEmpty();
    }

    // 던질 게 남았으면 자동으로 던지고, 같은 턴이 이어지면 TURN_CHANGED 없이 짧은 타이머로 다음 단계
    @Test
    void throwThenContinue() {
        throwsWill(YutResult.Gae);

        gameService.onTurnTimeout(ROOM, 0);

        assertThat(sent).containsExactly(MessageType.THROW_RESULT);
        assertThat(session.getCurrentTeam()).isZero();
        assertThat(session.getStoredResults()).containsExactly(YutResult.Gae);
        verify(turnTimerService).start(ROOM, 0, TimerType.TURN, GameService.TIMEOUT_STEP_DELAY);
    }

    // 윷·모가 나와도 30초를 새로 주지 않고 짧은 타이머로 이어서 진행
    @Test
    void extraThrowContinue() {
        throwsWill(YutResult.Mo);

        gameService.onTurnTimeout(ROOM, 0);

        assertThat(session.getRemainingThrows()).isEqualTo(1);
        assertThat(sent).doesNotContain(MessageType.TURN_CHANGED);
        verify(turnTimerService).start(ROOM, 0, TimerType.TURN, GameService.TIMEOUT_STEP_DELAY);
    }

    // 짧은 타이머가 다시 울리면 남은 단계를 이어서 하고, 결과를 다 쓰면 다음 팀
    @Test
    void continueUntilTurnEnds() {
        throwsWill(YutResult.Gae);

        gameService.onTurnTimeout(ROOM, 0);   // 1단계: 던지기 (개)
        gameService.onTurnTimeout(ROOM, 0);   // 2단계: 개로 자동 이동 → 턴 종료

        assertThat(sent).containsSequence(MessageType.THROW_RESULT, MessageType.MOVE_APPLIED, MessageType.TURN_CHANGED);
        assertThat(session.getCurrentTeam()).isEqualTo(1);
    }
}