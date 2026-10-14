package com.teamyutnori.yutnori.game.service;

import com.teamyutnori.yutnori.game.model.GamePhase;
import com.teamyutnori.yutnori.game.model.GameSession;
import com.teamyutnori.yutnori.game.repository.GameSessionRepository;
import com.teamyutnori.yutnori.game.service.AutoMovePolicy.AutoMove;
import com.teamyutnori.yutnori.reconnect.PlayerDisconnectedEvent;
import com.teamyutnori.yutnori.reconnect.PlayerReconnectedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

// 연결이 끊긴 플레이어 자동 진행
//
//  PLAYER_DISCONNECTED(PlayerDisconnectedEvent) → 그 팀을 자동으로 표시 (GameSession.setAuto)
//  그 팀 턴의 제한시간이 끝나면 GameService가 AutoTurnEvent 발행 → 여기서 대신 둔다
//    한 단계씩: 남은 던지기가 있으면 던지기, 없으면 AutoMovePolicy로 고른 이동 (MOVE_APPLIED auto = true)
//    단계 사이에 STEP_DELAY만큼 쉬어 클라 연출이 따라올 수 있게 한다
//    턴이 다음 팀으로 넘어가면 끝 (다음 턴도 끊겨 있으면 또 제한시간까지 기다린 뒤 자동)
//  PLAYER_RECONNECTED(PlayerReconnectedEvent) → 자동 해제. 진행 중이었으면 남은 행동은 본인에게 돌려준다
//
// 던지기·이동은 GameService.doThrow/doMove를 그대로 써서 사람이 둘 때와 같은 규칙(증강 포함)을 탄다
// 야바위꾼(다시 던지기)은 자동 진행에서 쓰지 않는다
@Slf4j
@Service
public class AutoPlayService {

    static final Duration STEP_DELAY = Duration.ofMillis(800);
    static final int MAX_STEPS = 30;   // 한 턴 안전장치 (보통 10번 안쪽에서 끝난다)

    private final GameSessionRepository sessionRepository;
    private final GameService gameService;
    private final AutoMovePolicy policy;
    private final TaskScheduler scheduler;
    private final Clock clock;
    private final Duration stepDelay;

    // 방마다 진행 중인 자동 턴 번호. 재접속·새 자동 턴이 오면 옛 단계는 스스로 멈춘다
    private final Map<String, Long> runs = new HashMap<>();
    private long nextRunId = 1;

    @Autowired
    public AutoPlayService(GameSessionRepository sessionRepository, GameService gameService, AutoMovePolicy policy,
                           @Qualifier("turnTimerScheduler") TaskScheduler scheduler, Clock clock) {
        this(sessionRepository, gameService, policy, scheduler, clock, STEP_DELAY);
    }

    AutoPlayService(GameSessionRepository sessionRepository, GameService gameService, AutoMovePolicy policy,
                    TaskScheduler scheduler, Clock clock, Duration stepDelay) {
        this.sessionRepository = sessionRepository;
        this.gameService = gameService;
        this.policy = policy;
        this.scheduler = scheduler;
        this.clock = clock;
        this.stepDelay = stepDelay;
    }

    // ═══════════ 자동 켜기·끄기 ═══════════

    @EventListener
    public void onDisconnected(PlayerDisconnectedEvent event) {
        withSession(event.roomCode(), session ->
                session.teamOf(event.playerId()).ifPresent(team -> session.setAuto(team, true)));
    }

    // 재접속하면 자동 해제. 진행 중인 단계는 다음 차례에 이를 보고 본인에게 턴을 돌려준다
    @EventListener
    public void onReconnected(PlayerReconnectedEvent event) {
        withSession(event.roomCode(), session ->
                session.teamOf(event.playerId()).ifPresent(team -> session.setAuto(team, false)));
    }

    // ═══════════ 자동 턴 ═══════════

    // GameService.onTurnTimeout에서 (세션 잠금 안에서) 발행된다. 첫 단계는 바로 진행
    @EventListener
    public void onAutoTurn(AutoTurnEvent event) {
        long runId;
        synchronized (runs) {
            runId = nextRunId++;
            runs.put(event.roomCode(), runId);
        }
        log.info("[AutoPlay] 자동 진행 시작 room={} team={}", event.roomCode(), event.team());
        step(event.roomCode(), event.team(), runId, 0);
    }

    // 한 단계 진행 후, 같은 팀 턴이 이어지면 다음 단계를 예약한다
    void step(String roomCode, int team, long runId, int count) {
        Optional<GameSession> found = sessionRepository.find(roomCode);
        if (found.isEmpty()) {
            endRun(roomCode, runId);
            return;
        }
        GameSession session = found.get();

        synchronized (session) {
            if (!isCurrentRun(roomCode, runId)) return;
            if (session.getPhase() != GamePhase.PLAYING || session.getCurrentTeam() != team || session.isRemoved(team)) {
                endRun(roomCode, runId);
                return;
            }
            // 재접속했으면 남은 행동은 본인에게 (타이머 새로 + TURN_CHANGED로 상태 다시 알림)
            if (!session.isAuto(team)) {
                endRun(roomCode, runId);
                gameService.resumeTurn(session);
                return;
            }
            if (count >= MAX_STEPS) {
                log.warn("[AutoPlay] 단계 수 초과로 턴 넘김 room={} team={}", roomCode, team);
                endRun(roomCode, runId);
                gameService.forceNextTurn(session);
                return;
            }

            try {
                act(session, team);
            } catch (RuntimeException e) {
                // 규칙상 막힌 경우 등. 멈춰 있지 않도록 다음 팀으로 넘긴다
                log.error("[AutoPlay] 자동 진행 실패, 턴 넘김 room={} team={}", roomCode, team, e);
                endRun(roomCode, runId);
                if (session.getPhase() == GamePhase.PLAYING && session.getCurrentTeam() == team) {
                    gameService.forceNextTurn(session);
                }
                return;
            }

            if (session.getPhase() != GamePhase.PLAYING || session.getCurrentTeam() != team) {
                endRun(roomCode, runId);   // 턴이 넘어갔거나 게임이 끝남
                return;
            }
        }

        int next = count + 1;
        scheduler.schedule(() -> step(roomCode, team, runId, next), clock.instant().plus(stepDelay));
    }

    // 던질 게 남았으면 던지기, 아니면 정책으로 고른 이동
    private void act(GameSession session, int team) {
        if (session.getRemainingThrows() > 0) {
            gameService.doThrow(session, team, false);   // 자동 진행은 야바위꾼을 쓰지 않으므로 기다리지 않음
            return;
        }

        Optional<AutoMove> move = policy.choose(session.getBoardState(), team, session.getStoredResults());
        if (move.isEmpty()) {
            // 보통은 doThrow의 endTurnIfNoAction이 먼저 넘긴다. 혹시 남은 경우
            gameService.forceNextTurn(session);
            return;
        }
        AutoMove m = move.get();
        gameService.doMove(session, team, m.pieceId(), m.moveCount(), m.destinationNodeId(), null, true);
    }

    // ═══════════ 내부 ═══════════

    private boolean isCurrentRun(String roomCode, long runId) {
        synchronized (runs) {
            Long current = runs.get(roomCode);
            return current != null && current == runId;
        }
    }

    private void endRun(String roomCode, long runId) {
        synchronized (runs) {
            runs.remove(roomCode, runId);
        }
    }

    private void withSession(String roomCode, java.util.function.Consumer<GameSession> action) {
        sessionRepository.find(roomCode).ifPresent(session -> {
            synchronized (session) {
                action.accept(session);
            }
        });
    }
}
