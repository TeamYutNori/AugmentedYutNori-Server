package com.teamyutnori.yutnori.game.service;

import com.teamyutnori.yutnori.config.GameProperties;
import com.teamyutnori.yutnori.game.augment.AugmentCatalog;
import com.teamyutnori.yutnori.game.augment.AugmentDraftService;
import com.teamyutnori.yutnori.game.augment.AugmentEffects;
import com.teamyutnori.yutnori.game.augment.RethrowValidator;
import com.teamyutnori.yutnori.game.board.TestBoards;
import com.teamyutnori.yutnori.game.model.GamePhase;
import com.teamyutnori.yutnori.game.model.GameSession;
import com.teamyutnori.yutnori.game.model.GameSetup;
import com.teamyutnori.yutnori.game.repository.GameSessionRepository;
import com.teamyutnori.yutnori.game.yut.YutResult;
import com.teamyutnori.yutnori.game.yut.YutThrowOutcome;
import com.teamyutnori.yutnori.game.yut.YutThrowService;
import com.teamyutnori.yutnori.reconnect.CommandLog;
import com.teamyutnori.yutnori.reconnect.TurnTimerService;
import com.teamyutnori.yutnori.reward.RewardPolicy;
import com.teamyutnori.yutnori.reward.RewardService;
import com.teamyutnori.yutnori.ws.RoomBroadcaster;
import com.teamyutnori.yutnori.ws.dto.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// 판에 말이 없는데 빽도: 야바위꾼이 있으면 직접 던졌을 때만 턴을 넘기지 않고 다시 던질 기회를 준다
// 팀 0(p0) 차례, 모든 말 대기 중
class GameServiceRethrowWaitTest {

    private static final String ROOM = "ROOM";

    private GameSession session;
    private YutThrowService yutThrowService;
    private GameService service;
    private final List<MessageType> sent = new ArrayList<>();

    @BeforeEach
    void setUp() {
        session = new GameSession(ROOM, new GameSetup(2, 2, "default", false), Map.of("p0", 0, "p1", 1),
                TestBoards.defaultBoard()).createPieces();
        session.setPhase(GamePhase.PLAYING);
        new TurnManager().startFirstTurn(session);   // 팀 0, 던지기 1번

        GameSessionRepository repository = new GameSessionRepository();
        repository.save(session);

        RoomBroadcaster broadcaster = mock(RoomBroadcaster.class);
        when(broadcaster.broadcast(any(), any(), any())).thenAnswer(inv -> {
            sent.add(inv.getArgument(1));
            return (long) sent.size();
        });
        yutThrowService = mock(YutThrowService.class);

        RethrowValidator rethrowValidator = new RethrowValidator(new AugmentCatalog(JsonMapper.builder().build()));
        service = new GameService(repository, new TurnManager(), yutThrowService,
                mock(AugmentDraftService.class), rethrowValidator, broadcaster,
                mock(GameProperties.class), mock(TurnTimerService.class), TestBoards.repository(), new CommandLog(),
                mock(AugmentEffects.class), new RewardPolicy(), mock(RewardService.class),
                mock(ApplicationEventPublisher.class), new AutoMovePolicy());
    }

    private void throwsWill(YutResult... results) {
        var stub = when(yutThrowService.throwYut());
        for (YutResult r : results) stub = stub.thenReturn(new YutThrowOutcome(new boolean[4], r));
    }

    @Test
    void 야바위꾼이_없으면_빽도는_바로_다음_팀() {
        throwsWill(YutResult.BackDo);

        service.throwYut(ROOM, "p0");

        assertThat(sent).containsExactly(MessageType.THROW_RESULT, MessageType.TURN_CHANGED);
        assertThat(session.getCurrentTeam()).isEqualTo(1);
    }

    @Test
    void 야바위꾼이_있으면_턴을_넘기지_않고_기다린다() {
        session.addAugment(0, RethrowValidator.RETHROW_AUGMENT_ID);
        throwsWill(YutResult.BackDo);

        service.throwYut(ROOM, "p0");

        assertThat(sent).containsExactly(MessageType.THROW_RESULT);
        assertThat(session.getCurrentTeam()).isZero();
    }

    @Test
    void 기다리다가_다시_던져도_못_움직이면_그때_넘긴다() {
        session.addAugment(0, RethrowValidator.RETHROW_AUGMENT_ID);
        throwsWill(YutResult.BackDo, YutResult.BackDo);

        service.throwYut(ROOM, "p0");
        service.rethrow(ROOM, "p0", RethrowValidator.RETHROW_AUGMENT_ID);

        assertThat(sent).containsExactly(MessageType.THROW_RESULT, MessageType.THROW_RESULT, MessageType.TURN_CHANGED);
        assertThat(session.getCurrentTeam()).isEqualTo(1);
    }

    @Test
    void 기다리다가_다시_던져서_움직일_수_있으면_턴_유지() {
        session.addAugment(0, RethrowValidator.RETHROW_AUGMENT_ID);
        throwsWill(YutResult.BackDo, YutResult.Gae);

        service.throwYut(ROOM, "p0");
        service.rethrow(ROOM, "p0", RethrowValidator.RETHROW_AUGMENT_ID);

        assertThat(sent).containsExactly(MessageType.THROW_RESULT, MessageType.THROW_RESULT);
        assertThat(session.getCurrentTeam()).isZero();
        assertThat(session.getStoredResults()).containsExactly(YutResult.Gae);
    }

    @Test
    void 기다리는_중에는_직접_턴을_넘길_수도_있다() {
        session.addAugment(0, RethrowValidator.RETHROW_AUGMENT_ID);
        throwsWill(YutResult.BackDo);

        service.throwYut(ROOM, "p0");
        service.passTurn(ROOM, "p0");

        assertThat(sent).containsExactly(MessageType.THROW_RESULT, MessageType.TURN_CHANGED);
        assertThat(session.getCurrentTeam()).isEqualTo(1);
    }

    @Test
    void 기다리는_중에_시간이_끝나면_다음_팀() {
        session.addAugment(0, RethrowValidator.RETHROW_AUGMENT_ID);
        throwsWill(YutResult.BackDo);
        service.throwYut(ROOM, "p0");

        service.onTurnTimeout(ROOM, 0);

        assertThat(sent).containsExactly(MessageType.THROW_RESULT, MessageType.TURN_CHANGED);
        assertThat(session.getCurrentTeam()).isEqualTo(1);
    }

    @Test
    void 시간_초과로_던진_빽도는_야바위꾼이_있어도_바로_넘긴다() {
        session.addAugment(0, RethrowValidator.RETHROW_AUGMENT_ID);
        throwsWill(YutResult.BackDo);

        service.onTurnTimeout(ROOM, 0);

        assertThat(sent).containsExactly(MessageType.THROW_RESULT, MessageType.TURN_CHANGED);
        assertThat(session.getCurrentTeam()).isEqualTo(1);
    }
}
