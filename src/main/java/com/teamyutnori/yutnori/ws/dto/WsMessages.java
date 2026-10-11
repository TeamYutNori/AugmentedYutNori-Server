package com.teamyutnori.yutnori.ws.dto;

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

    // 게임 결과 한 줄 (플레이어 = 팀). rank는 이번 판 등수(1부터, 같은 등수 다음은 건너뜀)
    // 기권·끊김으로 나간 플레이어는 꼴찌 등수 + rewardGold 0
    public record RankingEntry(String playerId, int team, int rank, int rewardGold) {}

    // rankings: 이번 판 등수 목록. 순위 계산·끊김 처리가 채운다
    public record GameEndedMessage(int winnerTeam, GameEndReason reason, List<RankingEntry> rankings) {
        public GameEndedMessage {
            rankings = rankings == null ? List.of() : List.copyOf(rankings);
        }

        // 순위를 아직 채우지 않는 곳용 (rankings = 빈 목록)
        public GameEndedMessage(int winnerTeam, GameEndReason reason) {
            this(winnerTeam, reason, List.of());
        }
    }

    public record GameSnapshotMessage(
            long lastSeq,
            GamePhase phase,
            int myTeam,
            int currentTeam,
            int remainingThrows,
            int turnNumber,
            List<YutResult> storedResults,
            Map<Integer, List<String>> ownedAugments,
            List<String> offeredAugments,
            Integer winnerTeam,
            TimerType timerType,
            int timerRemainingSec
    ) {}

}
