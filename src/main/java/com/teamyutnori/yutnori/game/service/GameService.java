package com.teamyutnori.yutnori.game.service;

import com.teamyutnori.yutnori.common.ConflictException;
import com.teamyutnori.yutnori.common.ForbiddenException;
import com.teamyutnori.yutnori.common.InvalidRequestException;
import com.teamyutnori.yutnori.common.NotFoundException;
import com.teamyutnori.yutnori.config.GameProperties;
import com.teamyutnori.yutnori.game.augment.AugmentDraftService;
import com.teamyutnori.yutnori.game.augment.RethrowValidator;
import com.teamyutnori.yutnori.game.dto.GameMessages.AugmentChoicesMessage;
import com.teamyutnori.yutnori.game.dto.GameMessages.AugmentSelectedMessage;
import com.teamyutnori.yutnori.game.dto.GameMessages.GameEndedMessage;
import com.teamyutnori.yutnori.game.dto.GameMessages.ThrowResultMessage;
import com.teamyutnori.yutnori.game.dto.GameMessages.TurnChangedMessage;
import com.teamyutnori.yutnori.game.model.GameEndReason;
import com.teamyutnori.yutnori.game.model.GamePhase;
import com.teamyutnori.yutnori.game.model.GameSession;
import com.teamyutnori.yutnori.game.model.GameSetup;
import com.teamyutnori.yutnori.game.repository.GameSessionRepository;
import com.teamyutnori.yutnori.game.yut.YutThrowOutcome;
import com.teamyutnori.yutnori.game.yut.YutThrowService;
import com.teamyutnori.yutnori.ws.RoomBroadcaster;
import com.teamyutnori.yutnori.ws.dto.MessageType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

import static com.teamyutnori.yutnori.game.exception.GameErrorCode.*;

// 게임 메시지 처리 입구.  흐름: 세션 꺼내기 → 검증(실패 시 common 예외) → 상태 변경(TurnManager 등) → 방송
// 예외는 MessageRouter가 BusinessException으로 잡아 ERROR 메시지로 보낸다
// 같은 방 요청이 동시에 와도 꼬이지 않게 세션 단위로 synchronized
@Service
@RequiredArgsConstructor
public class GameService {

    private final GameSessionRepository sessionRepository;
    private final TurnManager turnManager;
    private final YutThrowService yutThrowService;
    private final AugmentDraftService augmentDraftService;
    private final RethrowValidator rethrowValidator;
    private final RoomBroadcaster broadcaster;
    private final GameProperties gameProperties;

    // ═══════════ 2번(RoomService) → 게임 시작 ═══════════

    // GAME_START 전송 직후 호출. playerTeams: playerId → 팀 번호
    public void startGame(String roomCode, GameSetup setup, Map<String, Integer> playerTeams) {
        GameSession session = new GameSession(roomCode, setup, playerTeams);
        // 확인과 저장을 한 번에: 동시에 두 번 불려도 하나만 성공한다
        if (!sessionRepository.saveIfAbsent(session)) {
            throw new ConflictException(GAME_ALREADY_STARTED, "이미 진행 중인 게임이 있습니다: " + roomCode);
        }

        synchronized (session) {
            int count = gameProperties.augmentChoiceCount();
            int limitSec = toSeconds(gameProperties.augmentSelectTimeLimit());

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

            // 줄 수 있는 후보가 하나도 없으면(증강 목록이 비어 있는 경우 등) 선택 단계를 건너뛰고 바로 시작한다.
            // 이 처리가 없으면 아무도 고를 수 없어서 AUGMENT_SELECT 단계에서 영원히 멈춘다
            if (!session.hasAnyPendingOffer()) {
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
        }
    }

    // PASS_TURN  던질 것도 없고 움직일 말도 없을 때 클라이언트가 보낸다 (예: 판 위에 말이 없는데 빽도)
    // 서버는 말 위치를 모르므로 이 판단은 클라이언트에 맡기고, 남은 던지기가 0인지만 확인한다
    public void passTurn(String roomCode, String playerId) {
        GameSession session = getSession(roomCode);
        synchronized (session) {
            requirePhase(session, GamePhase.PLAYING);
            requireCurrentTurn(session, playerId);
            if (session.getRemainingThrows() > 0) {
                throw new InvalidRequestException(CANNOT_PASS_TURN, "아직 던질 수 있어서 턴을 넘길 수 없습니다.");
            }
            turnManager.nextTurn(session);
            broadcastTurnChanged(session);
        }
    }

    // ═══════════ 4번(MoveRelayService / TurnTimerService) → 결과 통보 ═══════════

    // 이동이 확정된 뒤 호출. 승리·추가 던지기·턴 넘김을 여기서 정한다
    public void onMoveApplied(String roomCode, int team, int moveCount, int capturedCount,
                              boolean isFinished, boolean hasRemainingAction) {
        GameSession session = getSession(roomCode);
        synchronized (session) {
            requirePhase(session, GamePhase.PLAYING);
            if (team != session.getCurrentTeam()) {
                throw new ForbiddenException(NOT_YOUR_TURN, "현재 차례가 아닌 팀의 이동입니다: " + team);
            }
            if (isFinished) {
                endGame(session, team, GameEndReason.FINISHED);
                return;
            }
            boolean turnChanged = turnManager.afterMove(session, moveCount, capturedCount, hasRemainingAction);
            if (turnChanged) {
                broadcastTurnChanged(session);
            }
        }
    }

    // 턴 제한시간 초과. turnNumber: 타이머를 시작할 때 받은 TURN_CHANGED의 턴 번호
    // 그 사이 턴이 이미 바뀌었으면(번호가 다르면) 늦게 도착한 타이머이므로 무시한다
    public void onTurnTimeout(String roomCode, int turnNumber) {
        GameSession session = getSession(roomCode);
        synchronized (session) {
            if (session.getPhase() != GamePhase.PLAYING) return;
            if (session.getTurnNumber() != turnNumber) return;
            turnManager.nextTurn(session);
            broadcastTurnChanged(session);
        }
    }

    // 증강 선택 제한시간 초과 → 아직 안 고른 팀은 첫 번째 후보로 자동 선택
    public void onAugmentSelectTimeout(String roomCode) {
        GameSession session = getSession(roomCode);
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
            endGame(session, winner, reason);
        }
    }

    // 결과 기록·정리가 끝난 뒤 세션 삭제
    public void removeGame(String roomCode) {
        sessionRepository.delete(roomCode);
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

    private void endGame(GameSession session, int winnerTeam, GameEndReason reason) {
        session.setPhase(GamePhase.ENDED);
        session.setWinnerTeam(winnerTeam);
        broadcaster.broadcast(session.getRoomCode(), MessageType.GAME_ENDED,
                new GameEndedMessage(winnerTeam, reason));
    }

    private void broadcastTurnChanged(GameSession session) {
        broadcaster.broadcast(session.getRoomCode(), MessageType.TURN_CHANGED,
                new TurnChangedMessage(session.getCurrentTeam(), session.getRemainingThrows(),
                        toSeconds(gameProperties.turnTimeLimit()), session.getTurnNumber()));
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

    private static int toSeconds(Duration duration) {
        return (int) duration.toSeconds();
    }
}
