package com.teamyutnori.yutnori.ws.dto;

import com.teamyutnori.yutnori.game.board.PieceState;
import com.teamyutnori.yutnori.game.model.GameEndReason;
import com.teamyutnori.yutnori.game.model.GamePhase;
import com.teamyutnori.yutnori.game.yut.YutResult;
import com.teamyutnori.yutnori.reconnect.TimerType;

import java.util.List;
import java.util.Map;

// 서버 → 클라이언트 메시지 payload 모음
public final class WsMessages {
    private WsMessages() {}

    public record PongMessage(long clientTime, long serverTime) {}
    public record ErrorMessage(String code, String message) {}
    public record PlayerDisconnectedMessage(String playerId, int graceSec) {}
    public record PlayerReconnectedMessage(String playerId) {}
    public record GameEndedMessage(int winnerTeam, GameEndReason reason){}
    public record PieceSnapshot(int pieceId, int team, PieceState state, int nodeId, Integer cameFromNodeId) {}

    public record GameSnapshotMessage(
            long lastSeq,
            GamePhase phase,
            int myTeam,
            int currentTeam,
            int remainingThrows,
            int turnNumber,
            List<YutResult> storedResults,
            List<PieceSnapshot> pieces,              // 추가
            Map<Integer, List<String>> ownedAugments,
            List<String> offeredAugments,
            Integer winnerTeam,
            TimerType timerType,
            int timerRemainingSec
    ) {}
}
