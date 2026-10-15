package com.teamyutnori.yutnori.ws.dto;

import com.teamyutnori.yutnori.game.board.PieceState;
import com.teamyutnori.yutnori.game.model.GameEndReason;
import com.teamyutnori.yutnori.game.model.GamePhase;
import com.teamyutnori.yutnori.game.yut.YutResult;
import com.teamyutnori.yutnori.reconnect.TimerType;
import com.teamyutnori.yutnori.room.model.Room;
import com.teamyutnori.yutnori.room.model.RoomMember;
import com.teamyutnori.yutnori.room.model.RoomStatus;

import java.util.List;
import java.util.Map;

// 서버 → 클라이언트 메시지 payload 모음
public final class WsMessages {
    private WsMessages() {}

    public record PongMessage(long clientTime, long serverTime) {}
    public record ErrorMessage(String code, String message) {}
    public record PlayerDisconnectedMessage(String playerId, int graceSec) {}
    public record PlayerReconnectedMessage(String playerId) {}
    public record PieceSnapshot(int pieceId, int team, PieceState state, int nodeId, Integer cameFromNodeId) {}

    // 플레이어 제거 (게임 중 나가기·재접속 유예시간 초과)
    // reason: FORFEIT = 나가기, DISCONNECTED = 재접속 실패
    // 1명만 남아 게임이 끝나면 PLAYER_REMOVED를 먼저 보내고 GAME_ENDED를 보낸다
    public record PlayerRemovedMessage(String playerId, int team, GameEndReason reason) {}

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
            List<PieceSnapshot> pieces,
            Map<Integer, List<String>> ownedAugments,
            List<String> offeredAugments,
            Integer winnerTeam,
            TimerType timerType,
            int timerRemainingSec
    ) {}

    // ROOM_STATE 메시지 안에서 사용하는 참가자 한 명의 정보
    // 클라이언트가 방에 누가 들어와 있는지, 어떤 팀인지, 준비 상태가 어떤지를 화면에 표시할 때 사용
    public record RoomMemberMessage(String playerId, String nickname, String iconId, int team, boolean ready) {

        // Room 도메인 객체의 참가자 정보를
        // 클라이언트에게 전달할 WebSocket 응답 형태로 변환
        public static RoomMemberMessage from(RoomMember member) {
            return new RoomMemberMessage(
                    member.getPlayerId(),
                    member.getNickname(),
                    member.getIconId(),
                    member.getTeam(),
                    member.isReady()
            );
        }
    }

    // 서버가 현재 방 전체 상태를 클라이언트들에게 전달할 때 사용하는 메시지
    // JOIN_ROOM, READY, LEAVE_ROOM 등으로 방 상태가 바뀔 때 최신 상태를 모든 참가자에게 ROOM_STATE로 방송하기 위해 사용
    public record RoomStateMessage(
            String roomCode,
            RoomStatus status,
            int currentPlayers,
            int maxPlayers,
            String hostPlayerId,
            List<RoomMemberMessage> members
    ) {
        // 현재 Room 객체의 상태를 기준으로
        // 클라이언트에게 보낼 ROOM_STATE 메시지 데이터를 생성
        public static RoomStateMessage from(Room room) {
            List<RoomMemberMessage> members = room.getMembers().stream().map(RoomMemberMessage::from).toList();
            return new RoomStateMessage(
                    room.getCode(),
                    room.getStatus(),
                    room.getCurrentPlayers(),
                    room.getMaxPlayers(),
                    room.getHostPlayerId(),
                    members
            );
        }
    }
}
