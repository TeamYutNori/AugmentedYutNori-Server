package com.teamyutnori.yutnori.game.service;

import com.teamyutnori.yutnori.common.ConflictException;
import com.teamyutnori.yutnori.common.ForbiddenException;
import com.teamyutnori.yutnori.common.InvalidRequestException;
import com.teamyutnori.yutnori.common.NotFoundException;
import com.teamyutnori.yutnori.config.GameProperties;
import com.teamyutnori.yutnori.game.augment.AugmentDraftService;
import com.teamyutnori.yutnori.game.augment.RethrowValidator;
import com.teamyutnori.yutnori.game.board.BoardGraph;
import com.teamyutnori.yutnori.game.board.BoardLayoutRepository;
import com.teamyutnori.yutnori.game.board.MoveResult;
import com.teamyutnori.yutnori.game.dto.GameRequests.MoveRequest;
import com.teamyutnori.yutnori.game.dto.GameMessages.AugmentChoicesMessage;
import com.teamyutnori.yutnori.game.dto.GameMessages.AugmentSelectedMessage;
import com.teamyutnori.yutnori.game.dto.GameMessages.MoveAppliedMessage;
import com.teamyutnori.yutnori.game.dto.GameMessages.ThrowResultMessage;
import com.teamyutnori.yutnori.game.dto.GameMessages.TurnChangedMessage;
import com.teamyutnori.yutnori.game.model.GameEndReason;
import com.teamyutnori.yutnori.game.model.GamePhase;
import com.teamyutnori.yutnori.game.model.GameSession;
import com.teamyutnori.yutnori.game.model.GameSetup;
import com.teamyutnori.yutnori.game.repository.GameSessionRepository;
import com.teamyutnori.yutnori.game.yut.YutThrowOutcome;
import com.teamyutnori.yutnori.game.yut.YutThrowService;
import com.teamyutnori.yutnori.reconnect.CommandLog;
import com.teamyutnori.yutnori.reconnect.GameEndPort;
import com.teamyutnori.yutnori.reconnect.TimerType;
import com.teamyutnori.yutnori.reconnect.TurnTimeoutEvent;
import com.teamyutnori.yutnori.reconnect.TurnTimerService;
import com.teamyutnori.yutnori.ws.RoomBroadcaster;
import com.teamyutnori.yutnori.ws.dto.MessageType;
import com.teamyutnori.yutnori.ws.dto.WsMessages.GameEndedMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

import static com.teamyutnori.yutnori.game.exception.GameErrorCode.*;

// 게임 메시지 처리 입구.  흐름: 세션 꺼내기 → 검증(실패 시 common 예외) → 상태 변경(TurnManager 등) → 방송
// 예외는 MessageRouter가 BusinessException으로 잡아 ERROR 메시지로 보낸다
// 같은 방 요청이 동시에 와도 꼬이지 않게 세션 단위로 synchronized
//
// 다른 담당과의 연결
//  - TurnTimerService(재접속·타이머 담당): 턴이 바뀌면 start, 증강 선택 시작 시 startAugmentSelect, 끝나면 cancel
//    시간이 다 되면 TurnTimeoutEvent를 받아 처리한다 (onTimerExpired)
//  - GameEndPort(재접속 담당이 정의): 끊김 기권 처리에서 게임 상태 조회·종료에 쓰도록 이 클래스가 구현한다
//  - CommandLog(재접속 담당): MOVE_APPLIED를 seq와 함께 기록해 재접속 때 다시 보낼 수 있게 한다
@Service
@RequiredArgsConstructor
public class GameService implements GameEndPort {

    private final GameSessionRepository sessionRepository;
    private final TurnManager turnManager;
    private final YutThrowService yutThrowService;
    private final AugmentDraftService augmentDraftService;
    private final RethrowValidator rethrowValidator;
    private final RoomBroadcaster broadcaster;
    private final GameProperties gameProperties;
    private final TurnTimerService turnTimerService;
    private final BoardLayoutRepository boardLayouts;
    private final CommandLog commandLog;

    // ═══════════ 2번(RoomService) → 게임 시작 ═══════════

    // GAME_START 전송 직후 호출. playerTeams: playerId → 팀 번호
    public void startGame(String roomCode, GameSetup setup, Map<String, Integer> playerTeams) {
        // 판 확인 → 이 게임 전용 판 생성 (GameSetup은 값만 들고 있어서 판이 실제로 있는지는 여기서 검사)
        if (!boardLayouts.exists(setup.boardLayoutId())) {
            throw new InvalidRequestException(INVALID_BOARD_LAYOUT, "없는 판입니다: " + setup.boardLayoutId());
        }
        BoardGraph board = boardLayouts.create(setup.boardLayoutId(), setup.allowSkipShortcut());

        GameSession session = new GameSession(roomCode, setup, playerTeams, board);
        // 확인과 저장을 한 번에: 동시에 두 번 불려도 하나만 성공한다
        if (!sessionRepository.saveIfAbsent(session)) {
            throw new ConflictException(GAME_ALREADY_STARTED, "이미 진행 중인 게임이 있습니다: " + roomCode);
        }

        synchronized (session) {
            int count = gameProperties.augmentChoiceCount();
            int limitSec = turnTimerService.limitSec(TimerType.AUGMENT_SELECT);

            // 1) 모든 팀의 후보를 먼저 뽑는다. 중간에 실패하면 세션을 지워 "시작도 못 하고 멈춘 게임"이 남지 않게 한다
            try {
                for (int team = 0; team < setup.teamCount(); team++) {
                    session.setOffered(team, augmentDraftService.draft(session, team, count));
                }
            } catch (RuntimeException e) {
                sessionRepository.delete(roomCode);
                throw e;
            }

            // 2) 다 뽑힌 뒤에 전송. 후보는 상대에게 보이지 않도록 해당 팀 플레이어에게만 보낸다
            for (int team = 0; team < setup.teamCount(); team++) {
                sendToTeam(session, team, MessageType.AUGMENT_CHOICES,
                        new AugmentChoicesMessage(team, session.getOfferedAugments().get(team), limitSec));
            }

            if (session.hasAnyPendingOffer()) {
                // 증강 선택 타이머 시작. 모든 팀이 동시에 고르므로 방 단위 타이머 하나만 건다
                // (TurnTimerService는 방마다 타이머 1개. team 값은 증강 선택에서 쓰지 않아 0을 넣는다)
                turnTimerService.startAugmentSelect(roomCode, 0);
            } else {
                // 줄 수 있는 후보가 하나도 없으면(증강 목록이 비어 있는 경우 등) 선택 단계를 건너뛰고 바로 시작한다.
                // 이 처리가 없으면 아무도 고를 수 없어서 AUGMENT_SELECT 단계에서 영원히 멈춘다
                session.setPhase(GamePhase.PLAYING);
                turnManager.startFirstTurn(session);
                broadcastTurnChanged(session);
            }
        }
    }

    // ═══════════ 클라이언트 요청 (ws/handler 에서 호출) ═══════════

    // SELECT_AUGMENT
    public void selectAugment(String roomCode, String playerId, String augmentId) {
        GameSession session = getSession(roomCode);
        synchronized (session) {
            requirePhase(session, GamePhase.AUGMENT_SELECT);
            int team = requireTeam(session, playerId);
            if (!session.hasOffer(team)) {
                throw new ConflictException(AUGMENT_ALREADY_SELECTED, "이미 증강을 선택했습니다.");
            }
            if (!session.isOffered(team, augmentId)) {
                throw new InvalidRequestException(INVALID_AUGMENT, "제시된 후보에 없는 증강입니다: " + augmentId);
            }
            confirmAugment(session, team, augmentId, false);
        }
    }

    // THROW_REQUEST
    public void throwYut(String roomCode, String playerId) {
        GameSession session = getSession(roomCode);
        synchronized (session) {
            requirePhase(session, GamePhase.PLAYING);
            int team = requireCurrentTurn(session, playerId);
            if (session.getRemainingThrows() <= 0) {
                throw new InvalidRequestException(NO_THROWS_LEFT, "남은 던지기 횟수가 없습니다.");
            }

            YutThrowOutcome outcome = yutThrowService.throwYut();
            turnManager.applyThrow(session, outcome.result());

            broadcaster.broadcast(session.getRoomCode(), MessageType.THROW_RESULT,
                    new ThrowResultMessage(team, outcome.sticks(), outcome.result(), false));

            // 더 던질 것도, 움직일 말도 없으면 바로 다음 팀 (예: 판에 말이 없는데 빽도)
            if (turnManager.endTurnIfNoAction(session)) {
                broadcastTurnChanged(session);
            }
        }
    }

    // RETHROW_REQUEST
    public void rethrow(String roomCode, String playerId, String augmentId) {
        GameSession session = getSession(roomCode);
        synchronized (session) {
            requirePhase(session, GamePhase.PLAYING);
            int team = requireCurrentTurn(session, playerId);
            if (!RethrowValidator.RETHROW_AUGMENT_ID.equals(augmentId)
                    || !rethrowValidator.canRethrow(session, team)) {
                throw new InvalidRequestException(RETHROW_NOT_ALLOWED, "지금은 다시 던지기를 사용할 수 없습니다.");
            }

            YutThrowOutcome outcome = yutThrowService.throwYut();
            session.removeAugment(team, RethrowValidator.RETHROW_AUGMENT_ID);   // 1회용 증강 소모
            turnManager.applyRethrow(session, outcome.result());

            broadcaster.broadcast(session.getRoomCode(), MessageType.THROW_RESULT,
                    new ThrowResultMessage(team, outcome.sticks(), outcome.result(), true));

            if (turnManager.endTurnIfNoAction(session)) {
                broadcastTurnChanged(session);
            }
        }
    }

    // PASS_TURN  던질 것도 없고 움직일 말도 없을 때 턴 넘기기
    // 보통은 던지기 직후 서버가 endTurnIfNoAction으로 알아서 넘긴다. 이건 혹시 남은 경우를 위한 수동 넘김
    // 서버가 판을 알고 있으므로 "정말 움직일 말이 없는지"까지 확인한다
    public void passTurn(String roomCode, String playerId) {
        GameSession session = getSession(roomCode);
        synchronized (session) {
            requirePhase(session, GamePhase.PLAYING);
            requireCurrentTurn(session, playerId);
            if (session.getRemainingThrows() > 0) {
                throw new InvalidRequestException(CANNOT_PASS_TURN, "아직 던질 수 있어서 턴을 넘길 수 없습니다.");
            }
            if (turnManager.hasAnyMove(session)) {
                throw new InvalidRequestException(CANNOT_PASS_TURN, "움직일 수 있는 말이 있어서 턴을 넘길 수 없습니다.");
            }
            turnManager.nextTurn(session);
            broadcastTurnChanged(session);
        }
    }

    // MOVE  클라는 "어떤 말을, 어떤 윷 결과(칸 수)로, 어느 칸으로"만 보낸다
    // 갈 수 있는 칸인지, 잡기·업기·완주, 턴 유지 여부는 전부 서버가 판(BoardState)으로 계산한다
    // 흐름: 검증 → 판에 적용 → MOVE_APPLIED 방송 → 팀 완주면 게임 종료, 아니면 턴 처리
    public void move(String roomCode, String playerId, MoveRequest request) {
        GameSession session = getSession(roomCode);
        synchronized (session) {
            requirePhase(session, GamePhase.PLAYING);
            int team = requireCurrentTurn(session, playerId);

            // 윷·모로 남은 던지기가 있으면 먼저 다 던져야 한다 (Unity GameRules.MovePiece와 같은 규칙)
            if (session.getRemainingThrows() > 0) {
                throw new InvalidRequestException(THROW_REMAINING, "남은 던지기를 먼저 해야 합니다.");
            }
            // 이 게임에 있는 자기 팀 말인지
            int pieceId = request.pieceId();
            if (!session.getSetup().isValidPieceId(pieceId) || GameSetup.teamOf(pieceId) != team) {
                throw new ForbiddenException(NOT_YOUR_PIECE, "자기 팀 말만 움직일 수 있습니다: " + pieceId);
            }
            // 던져서 저장된 결과로만 움직일 수 있다
            boolean hasResult = session.getStoredResults().stream().anyMatch(r -> r.steps() == request.moveCount());
            if (!hasResult) {
                throw new InvalidRequestException(INVALID_MOVE_RESULT,
                        "저장된 윷 결과 중 " + request.moveCount() + "칸 결과가 없습니다.");
            }

            // 판 규칙상 그 칸으로 갈 수 있을 때만 적용 (지름길·빽도·증강 보정 포함)
            MoveResult result = session.getBoardState()
                    .tryMove(pieceId, request.moveCount(), request.destinationNodeId())
                    .orElseThrow(() -> new InvalidRequestException(INVALID_MOVE,
                            "갈 수 없는 칸입니다: 말 " + pieceId + ", " + request.moveCount() + "칸 → " + request.destinationNodeId()));

            // 재접속 시 놓친 이동을 다시 보낼 수 있게 seq와 함께 기록 (재접속 담당 CommandLog)
            MoveAppliedMessage applied = MoveAppliedMessage.of(team, result, request.stateHash());
            long seq = broadcaster.broadcast(session.getRoomCode(), MessageType.MOVE_APPLIED, applied);
            commandLog.append(session.getRoomCode(), seq, MessageType.MOVE_APPLIED, team, applied);

            // 팀 말이 전부 들어왔으면 승리 (MVP: 먼저 다 들어온 팀이 이김. 순위 보상은 이후 작업)
            if (session.getBoardState().teamFinished(team)) {
                finishGame(session, team, GameEndReason.FINISHED);
                return;
            }

            // 쓴 윷 결과 제거, 잡으면 한 번 더, 남은 행동 없으면 다음 팀
            if (turnManager.afterMove(session, result)) {
                broadcastTurnChanged(session);
            }
        }
    }

    // TurnTimerService가 제한시간이 끝나면 발행하는 이벤트 (타이머 스레드에서 호출됨)
    // 늦게 울린 옛 타이머는 TurnTimerService가 먼저 걸러 준다
    @EventListener
    public void onTimerExpired(TurnTimeoutEvent event) {
        switch (event.type()) {
            case TURN -> onTurnTimeout(event.roomCode(), event.team());
            case AUGMENT_SELECT -> onAugmentSelectTimeout(event.roomCode());
        }
    }

    // 턴 제한시간 초과 → 다음 팀으로 넘긴다
    // 타이머 스레드에서 오므로 세션이 없으면 예외 대신 조용히 무시한다
    public void onTurnTimeout(String roomCode, int team) {
        Optional<GameSession> found = sessionRepository.find(roomCode);
        if (found.isEmpty()) return;
        GameSession session = found.get();
        synchronized (session) {
            if (session.getPhase() != GamePhase.PLAYING) return;
            if (session.getCurrentTeam() != team) return;   // 그 사이 턴이 이미 바뀌었으면 무시 (이중 안전장치)
            turnManager.nextTurn(session);
            broadcastTurnChanged(session);
        }
    }

    // 증강 선택 제한시간 초과 → 아직 안 고른 팀은 첫 번째 후보로 자동 선택
    public void onAugmentSelectTimeout(String roomCode) {
        Optional<GameSession> found = sessionRepository.find(roomCode);
        if (found.isEmpty()) return;
        GameSession session = found.get();
        synchronized (session) {
            if (session.getPhase() != GamePhase.AUGMENT_SELECT) return;
            for (int team = 0; team < session.getSetup().teamCount(); team++) {
                if (session.hasOffer(team)) {
                    String first = session.getOfferedAugments().get(team).get(0);
                    confirmAugment(session, team, first, true);
                }
            }
        }
    }

    // 기권·재접속 실패 (MVP는 2팀이라 남은 팀이 승리)
    public void forfeit(String roomCode, int loserTeam, GameEndReason reason) {
        GameSession session = getSession(roomCode);
        synchronized (session) {
            if (session.getPhase() == GamePhase.ENDED) return;
            int winner = (loserTeam + 1) % session.getSetup().teamCount();
            finishGame(session, winner, reason);
        }
    }

    // 결과 기록·정리가 끝난 뒤 세션 삭제
    public void removeGame(String roomCode) {
        sessionRepository.delete(roomCode);
    }

    // ═══════════ GameEndPort 구현 (재접속 담당 DisconnectTimeoutHandler가 호출) ═══════════

    // 진행 중인 게임인지 (증강 선택 중도 게임 중으로 본다. 없는 방·끝난 게임이면 false)
    @Override
    public boolean isPlaying(String roomCode) {
        return sessionRepository.find(roomCode)
                .map(session -> {
                    synchronized (session) {
                        return session.getPhase() != GamePhase.ENDED;
                    }
                })
                .orElse(false);
    }

    // 플레이어의 팀 번호
    @Override
    public Optional<Integer> findTeam(String roomCode, String playerId) {
        return sessionRepository.find(roomCode).flatMap(session -> session.teamOf(playerId));
    }

    // 게임 종료 상태로만 바꾼다.
    // GAME_ENDED 전송과 타이머 취소는 DisconnectTimeoutHandler가 직접 하므로 여기서 또 하면 종료 메시지가 두 번 나간다
    @Override
    public void endGame(String roomCode, int winnerTeam) {
        sessionRepository.find(roomCode).ifPresent(session -> {
            synchronized (session) {
                if (session.getPhase() == GamePhase.ENDED) return;
                session.setPhase(GamePhase.ENDED);
                session.setWinnerTeam(winnerTeam);
            }
        });
    }

    // ═══════════ 내부 처리 ═══════════

    private void confirmAugment(GameSession session, int team, String augmentId, boolean autoSelected) {
        session.addAugment(team, augmentId);
        session.clearOffered(team);
        broadcaster.broadcast(session.getRoomCode(), MessageType.AUGMENT_SELECTED,
                new AugmentSelectedMessage(team, augmentId, autoSelected));

        // 모든 팀이 골랐으면 게임 시작
        if (!session.hasAnyPendingOffer()) {
            session.setPhase(GamePhase.PLAYING);
            turnManager.startFirstTurn(session);
            broadcastTurnChanged(session);
        }
    }

    // 게임 안에서 끝났을 때(완주·기권): 상태 변경 + 타이머 취소 + GAME_ENDED 전송
    private void finishGame(GameSession session, int winnerTeam, GameEndReason reason) {
        session.setPhase(GamePhase.ENDED);
        session.setWinnerTeam(winnerTeam);
        turnTimerService.cancel(session.getRoomCode());
        broadcaster.broadcast(session.getRoomCode(), MessageType.GAME_ENDED,
                new GameEndedMessage(winnerTeam, reason));
    }

    // 새 턴 알림 + 턴 타이머 시작 (start는 이전 타이머를 취소하고 새로 건다)
    private void broadcastTurnChanged(GameSession session) {
        turnTimerService.start(session.getRoomCode(), session.getCurrentTeam());
        broadcaster.broadcast(session.getRoomCode(), MessageType.TURN_CHANGED,
                new TurnChangedMessage(session.getCurrentTeam(), session.getRemainingThrows(),
                        turnTimerService.limitSec(TimerType.TURN), session.getTurnNumber()));
    }

    private void sendToTeam(GameSession session, int team, MessageType type, Object payload) {
        session.getPlayerTeams().forEach((playerId, playerTeam) -> {
            if (playerTeam == team) {
                broadcaster.sendTo(session.getRoomCode(), playerId, type, payload);
            }
        });
    }

    // ═══════════ 검증 (실패 시 common 예외) ═══════════

    private GameSession getSession(String roomCode) {
        return sessionRepository.find(roomCode)
                .orElseThrow(() -> new NotFoundException(GAME_NOT_FOUND, "진행 중인 게임이 없습니다: " + roomCode));
    }

    private void requirePhase(GameSession session, GamePhase expected) {
        if (session.getPhase() != expected) {
            throw new ConflictException(INVALID_PHASE,
                    "지금은 처리할 수 없는 요청입니다. (현재 단계: " + session.getPhase() + ")");
        }
    }

    private int requireTeam(GameSession session, String playerId) {
        return session.teamOf(playerId)
                .orElseThrow(() -> new ForbiddenException(PLAYER_NOT_IN_GAME, "이 게임의 참가자가 아닙니다."));
    }

    private int requireCurrentTurn(GameSession session, String playerId) {
        int team = requireTeam(session, playerId);
        if (team != session.getCurrentTeam()) {
            throw new ForbiddenException(NOT_YOUR_TURN, "내 차례가 아닙니다.");
        }
        return team;
    }
}
