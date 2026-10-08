package com.teamyutnori.yutnori.game.service;

import com.teamyutnori.yutnori.common.ConflictException;
import com.teamyutnori.yutnori.common.ForbiddenException;
import com.teamyutnori.yutnori.common.InvalidRequestException;
import com.teamyutnori.yutnori.common.NotFoundException;
import com.teamyutnori.yutnori.game.dto.GameMessages.MoveAppliedMessage;
import com.teamyutnori.yutnori.game.dto.GameRequests.MoveRequest;
import com.teamyutnori.yutnori.game.model.GamePhase;
import com.teamyutnori.yutnori.game.model.GameSession;
import com.teamyutnori.yutnori.game.model.GameSetup;
import com.teamyutnori.yutnori.game.repository.GameSessionRepository;
import com.teamyutnori.yutnori.reconnect.CommandLog;
import com.teamyutnori.yutnori.ws.RoomBroadcaster;
import com.teamyutnori.yutnori.ws.dto.MessageType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.teamyutnori.yutnori.game.exception.GameErrorCode.*;

@Service
@RequiredArgsConstructor
public class MoveRelayService {

    private final GameSessionRepository sessionRepository;
    private final GameService gameService;
    private final RoomBroadcaster broadcaster;
    private final CommandLog commandLog;

    public void move(String roomCode, String playerId, MoveRequest request){
        GameSession session = sessionRepository.find(roomCode).orElseThrow(() -> new NotFoundException(GAME_NOT_FOUND, "진행 중인 게임이 없습니다.: " + roomCode));

        synchronized (session){
            int team = validate(session, playerId, request);

            MoveAppliedMessage applied = new MoveAppliedMessage(team, request.pieceId(), request.moveCount(), request.destinationNodeId(), request.stateHash());
            long seq = broadcaster.broadcast(roomCode, MessageType.MOVE_APPLIED, applied);
            commandLog.append(roomCode, seq, MessageType.MOVE_APPLIED, team, applied);

            gameService.onMoveApplied(roomCode, team, request.moveCount(), request.capturedCount(), request.isFinished(), request.hasRemainingAction());
        }
    }

    private int validate(GameSession session, String playerId, MoveRequest request){
        if(session.getPhase() != GamePhase.PLAYING){
            throw new ConflictException(INVALID_PHASE, "지금은 이동할 수 없습니다. (현재 단계: " + session.getPhase() + ")");
        }

        int team = session.teamOf(playerId).orElseThrow(() -> new ForbiddenException(PLAYER_NOT_IN_GAME, "이 게임의 참가자가 아닙니다."));
        if(team != session.getCurrentTeam()) throw new ForbiddenException(NOT_YOUR_TURN, "내 차례가 아닙니다.");

        int pieceId = request.pieceId();
        int slot = pieceId % GameSetup.TEAM_ID_STRIDE;
        if(pieceId < 0 || GameSetup.teamOf(pieceId) != team || slot >= session.getSetup().piecesPerTeam()){
            throw new InvalidRequestException(INVALID_PIECE, "내 팀의 말이 아닙니다.: " + pieceId);
        }

        boolean hasResult = session.getStoredResults().stream().anyMatch(result -> result.steps() == request.moveCount());
        if(!hasResult)
            throw new InvalidRequestException(INVALID_MOVE_RESULT, "저장된 윷 결과 중 " + request.moveCount() + "칸 결과가 없습니다.");

        if(request.capturedCount() < 0) throw new InvalidRequestException(INVALID_MOVE_REPORT, "잡은 말 수가 올바르지 않습니다.");
        return team;
    }
}
