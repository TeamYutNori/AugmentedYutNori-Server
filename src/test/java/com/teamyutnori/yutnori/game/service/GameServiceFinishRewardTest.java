package com.teamyutnori.yutnori.game.service;

import com.teamyutnori.yutnori.config.GameProperties;
import com.teamyutnori.yutnori.game.augment.AugmentDraftService;
import com.teamyutnori.yutnori.game.augment.AugmentEffects;
import com.teamyutnori.yutnori.game.augment.RethrowValidator;
import com.teamyutnori.yutnori.game.board.BoardState;
import com.teamyutnori.yutnori.game.board.TestBoards;
import com.teamyutnori.yutnori.game.dto.GameRequests.MoveRequest;
import com.teamyutnori.yutnori.game.model.GameEndReason;
import com.teamyutnori.yutnori.game.model.GamePhase;
import com.teamyutnori.yutnori.game.model.GameSession;
import com.teamyutnori.yutnori.game.model.GameSetup;
import com.teamyutnori.yutnori.game.repository.GameSessionRepository;
import com.teamyutnori.yutnori.game.yut.YutResult;
import com.teamyutnori.yutnori.game.yut.YutThrowService;
import com.teamyutnori.yutnori.player.entity.Player;
import com.teamyutnori.yutnori.player.repository.PlayerRepository;
import com.teamyutnori.yutnori.reconnect.CommandLog;
import com.teamyutnori.yutnori.reconnect.TurnTimerService;
import com.teamyutnori.yutnori.reward.RewardPolicy;
import com.teamyutnori.yutnori.reward.RewardService;
import com.teamyutnori.yutnori.ws.RoomBroadcaster;
import com.teamyutnori.yutnori.ws.dto.MessageType;
import com.teamyutnori.yutnori.ws.dto.WsMessages.GameEndedMessage;
import com.teamyutnori.yutnori.ws.dto.WsMessages.RankingEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// 말이 전부 들어와 정상으로 끝났을 때: MOVE → 완주 판정 → 순위 → 실제 보유 골드 지급까지
// 4팀, 팀당 말 2개. 팀 0이 마지막 말을 넣어 승리 (19번 칸에서 개 2칸 → 도착 0)
class GameServiceFinishRewardTest {

    private static final String ROOM = "ROOM";

    private GameSession session;
    private RoomBroadcaster broadcaster;
    private final Map<String, Player> players = new HashMap<>();
    private GameService service;

    @BeforeEach
    void setUp() {
        session = new GameSession(ROOM, new GameSetup(4, 2, "default", false),
                Map.of("p0", 0, "p1", 1, "p2", 2, "p3", 3), TestBoards.defaultBoard()).createPieces();
        session.setPhase(GamePhase.PLAYING);

        PlayerRepository playerRepository = mock(PlayerRepository.class);
        for (String id : new String[]{"p0", "p1", "p2", "p3"}) {
            players.put(id, Player.createFirebase(id, "uid-" + id, id + "@example.com"));
        }
        when(playerRepository.findByPlayerId(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(players.get(inv.getArgument(0, String.class))));

        GameSessionRepository repository = new GameSessionRepository();
        repository.save(session);
        broadcaster = mock(RoomBroadcaster.class);
        when(broadcaster.broadcast(any(), any(), any())).thenReturn(5L);   // CommandLog는 seq 1 이상만 받는다

        service = new GameService(repository, new TurnManager(), mock(YutThrowService.class),
                mock(AugmentDraftService.class), mock(RethrowValidator.class), broadcaster,
                mock(GameProperties.class), mock(TurnTimerService.class), TestBoards.repository(), new CommandLog(),
                mock(AugmentEffects.class), new RewardPolicy(), new RewardService(playerRepository),
                mock(ApplicationEventPublisher.class), mock(AutoMovePolicy.class));
    }

    // 말 하나를 19번 칸까지 (한 번 더 개 2칸이면 도착)
    private void toLastNode(int pieceId) {
        BoardState board = session.getBoardState();
        int[][] steps = {{4, 4}, {5, 9}, {5, 14}, {5, 19}};
        for (int[] s : steps) assertTrue(board.tryMove(pieceId, s[0], s[1]).isPresent(), "이동 실패: " + pieceId);
    }

    private void finishOne(int pieceId) {
        toLastNode(pieceId);
        assertTrue(session.getBoardState().tryMove(pieceId, 2, 0).isPresent(), "도착 실패: " + pieceId);
    }

    // 팀 0 차례에 마지막 말(0번)을 개 2칸으로 넣는다
    private GameEndedMessage finishByTeam0() {
        session.setCurrentTeam(0);
        session.getStoredResults().add(YutResult.Gae);
        service.move(ROOM, "p0", new MoveRequest(0, 2, 0, "0123456789abcdef"));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(broadcaster).broadcast(eq(ROOM), eq(MessageType.GAME_ENDED), captor.capture());
        return (GameEndedMessage) captor.getValue();
    }

    private RankingEntry of(GameEndedMessage ended, int team) {
        return ended.rankings().stream().filter(e -> e.team() == team).findFirst().orElseThrow();
    }

    @Test
    void 아무도_안_나가고_완주하면_4인_표로_남은_사람_모두_골드를_받는다() {
        finishOne(200);     // 팀 2: 1개 (팀 0 말이 19번 칸에 가기 전에 지나가야 잡히지 않는다)
        finishOne(1);       // 팀 0: 1개 들어옴, 0번은 19번 칸
        toLastNode(0);
        session.setRoundNumber(8);

        GameEndedMessage ended = finishByTeam0();

        assertThat(ended.reason()).isEqualTo(GameEndReason.FINISHED);
        assertThat(ended.winnerTeam()).isZero();
        assertThat(of(ended, 0).rewardGold()).isEqualTo(500);   // 1등
        assertThat(of(ended, 2).rewardGold()).isEqualTo(400);   // 2등
        assertThat(of(ended, 1).rewardGold()).isEqualTo(250);   // 3등 공동
        assertThat(of(ended, 3).rewardGold()).isEqualTo(250);
        assertThat(ended.rankings()).noneMatch(RankingEntry::leftGame);

        // 실제 보유 골드에 들어갔는지
        assertThat(players.get("p0").getGold()).isEqualTo(500);
        assertThat(players.get("p2").getGold()).isEqualTo(400);
        assertThat(players.get("p1").getGold()).isEqualTo(250);
        assertThat(players.get("p3").getGold()).isEqualTo(250);
    }

    @Test
    void 첫_바퀴에_1명_나가고_완주하면_남은_3명이_3인_표로_골드를_받는다() {
        session.setRoundNumber(1);          // 팀 1이 첫 바퀴에 나감 (기준 전)
        session.markRemoved(1);
        session.getBoardState().removeTeam(1);

        finishOne(200);                     // 팀 2: 1개, 팀 3: 0개
        finishOne(1);
        toLastNode(0);
        session.setRoundNumber(8);

        GameEndedMessage ended = finishByTeam0();

        assertThat(of(ended, 0).rewardGold()).isEqualTo(400);   // 3인 1등
        assertThat(of(ended, 2).rewardGold()).isEqualTo(200);   // 3인 2등
        assertThat(of(ended, 3).rewardGold()).isEqualTo(100);   // 3인 3등
        assertThat(of(ended, 1).rewardGold()).isZero();
        assertThat(of(ended, 1).leftGame()).isTrue();

        assertThat(players.get("p0").getGold()).isEqualTo(400);
        assertThat(players.get("p2").getGold()).isEqualTo(200);
        assertThat(players.get("p3").getGold()).isEqualTo(100);
        assertThat(players.get("p1").getGold()).isZero();
    }

    @Test
    void 두_번째_바퀴에_1명_나가고_완주하면_남은_3명이_4인_표로_골드를_받는다() {
        session.setRoundNumber(2);          // 두 번째 바퀴에 나감 (기준 뒤)
        session.markRemoved(1);
        session.getBoardState().removeTeam(1);

        finishOne(200);
        finishOne(1);
        toLastNode(0);
        session.setRoundNumber(8);

        GameEndedMessage ended = finishByTeam0();

        assertThat(of(ended, 0).rewardGold()).isEqualTo(500);
        assertThat(of(ended, 2).rewardGold()).isEqualTo(400);
        assertThat(of(ended, 3).rewardGold()).isEqualTo(250);
        assertThat(players.get("p3").getGold()).isEqualTo(250);
        assertThat(players.get("p1").getGold()).isZero();
    }

    // 진우님 테스트 상황 (match 11): 팀 0이 나, 팀 1·2가 첫 바퀴에 나가고 팀 3은 두 번째 바퀴에 나감
    // 예전 구현(턴 번호 ≤ 시작 인원)은 팀 3의 이탈(턴 4)도 기준 전으로 봐서 1등이 0골드였다
    @Test
    void 첫_바퀴에_2명_두_번째_바퀴에_1명_나가면_1등은_2인_표_200() {
        TurnManager turns = new TurnManager();
        turns.startFirstTurn(session);              // 턴 1: 팀 0 (1라운드)
        turns.nextTurn(session);                    // 턴 2: 팀 1
        service.leaveGame(ROOM, "p1");              // 팀 1 자기 차례에 나감 → 턴 3: 팀 2
        service.leaveGame(ROOM, "p2");              // 팀 2 나감 → 턴 4: 팀 3 (아직 1라운드)
        assertThat(session.getRoundNumber()).isEqualTo(1);
        turns.nextTurn(session);                    // 턴 5: 팀 0 (2라운드)
        turns.nextTurn(session);                    // 턴 6: 팀 3 (2라운드)
        assertThat(session.getRoundNumber()).isEqualTo(2);
        service.leaveGame(ROOM, "p3");              // 팀 3 두 번째 바퀴에 나감 → 팀 0 혼자 남아 종료

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(broadcaster).broadcast(eq(ROOM), eq(MessageType.GAME_ENDED), captor.capture());
        GameEndedMessage ended = (GameEndedMessage) captor.getValue();

        assertThat(GameRanking.rewardPlayerCount(session, new RewardPolicy())).isEqualTo(2);
        assertThat(of(ended, 0).rewardGold()).isEqualTo(200);
        assertThat(players.get("p0").getGold()).isEqualTo(200);
        assertThat(of(ended, 3).leftGame()).isTrue();
    }
}
