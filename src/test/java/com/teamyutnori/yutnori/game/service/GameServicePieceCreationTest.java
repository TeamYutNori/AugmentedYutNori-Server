package com.teamyutnori.yutnori.game.service;

import com.teamyutnori.yutnori.config.GameProperties;
import com.teamyutnori.yutnori.game.augment.AugmentCatalog;
import com.teamyutnori.yutnori.game.augment.AugmentDraftService;
import com.teamyutnori.yutnori.game.augment.AugmentEffects;
import com.teamyutnori.yutnori.game.augment.AugmentIds;
import com.teamyutnori.yutnori.game.augment.RethrowValidator;
import com.teamyutnori.yutnori.game.board.TestBoards;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// 말은 GAME_START 때가 아니라 모든 팀의 증강이 정해진 순간 만든다 (클라와 같은 시점·규칙)
// 3팀, 팀당 말 4개. 팀 1이 노장을 고르면 100·101·102만
class GameServicePieceCreationTest {

    private static final String ROOM = "ROOM";

    private GameSessionRepository repository;
    private GameService service;

    @BeforeEach
    void setUp() {
        repository = new GameSessionRepository();
        AugmentDraftService draft = mock(AugmentDraftService.class);
        when(draft.draft(any(), anyInt(), anyInt())).thenReturn(List.of(AugmentIds.VETERAN, AugmentIds.SPRINTER));
        GameProperties properties = mock(GameProperties.class);
        when(properties.augmentChoiceCount()).thenReturn(2);

        AugmentCatalog catalog = new AugmentCatalog(JsonMapper.builder().build());
        service = new GameService(repository, new TurnManager(), mock(YutThrowService.class),
                draft, new RethrowValidator(catalog), mock(RoomBroadcaster.class),
                properties, mock(TurnTimerService.class), TestBoards.repository(), new CommandLog(),
                new AugmentEffects(catalog), new RewardPolicy(), mock(RewardService.class),
                mock(ApplicationEventPublisher.class), new AutoMovePolicy());

        service.startGame(ROOM, new GameSetup(3, 4, "default", false), Map.of("p0", 0, "p1", 1, "p2", 2));
    }

    private GameSession session() {
        return repository.find(ROOM).orElseThrow();
    }

    private List<Integer> idsOf(int team) {
        return session().getBoardState().getPiecesOf(team).stream().map(p -> p.getId()).sorted().toList();
    }

    @Test
    void 증강을_고르는_동안에는_말이_없다() {
        service.selectAugment(ROOM, "p0", AugmentIds.SPRINTER);
        service.selectAugment(ROOM, "p1", AugmentIds.VETERAN);

        assertThat(session().getPhase()).isEqualTo(GamePhase.AUGMENT_SELECT);
        assertThat(session().getBoardState().getPieces()).isEmpty();
    }

    @Test
    void 마지막_팀이_고르면_말을_만들고_노장은_하나_적다() {
        service.selectAugment(ROOM, "p0", AugmentIds.SPRINTER);
        service.selectAugment(ROOM, "p1", AugmentIds.VETERAN);
        service.selectAugment(ROOM, "p2", AugmentIds.SPRINTER);

        assertThat(session().getPhase()).isEqualTo(GamePhase.PLAYING);
        assertThat(idsOf(0)).containsExactly(0, 1, 2, 3);
        assertThat(idsOf(1)).containsExactly(100, 101, 102);
        assertThat(idsOf(2)).containsExactly(200, 201, 202, 203);
    }

    @Test
    void 증강_선택_중_나간_팀은_말이_없다_골랐어도() {
        service.selectAugment(ROOM, "p2", AugmentIds.VETERAN);
        service.leaveGame(ROOM, "p2");
        service.selectAugment(ROOM, "p0", AugmentIds.SPRINTER);
        service.selectAugment(ROOM, "p1", AugmentIds.SPRINTER);

        assertThat(session().getPhase()).isEqualTo(GamePhase.PLAYING);
        assertThat(idsOf(2)).isEmpty();
        assertThat(idsOf(0)).hasSize(4);
        assertThat(idsOf(1)).hasSize(4);
    }
}
