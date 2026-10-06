package com.teamyutnori.yutnori.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// 방마다 지금 연결된 플레이어 세션을 기억하는 명부.
// Broadcaster가 방 전체에 보낼 때, 재접속 시 세션을 교체할 때 사용한다.
// 방·게임 상태가 아니라 "현재 연결"만 관리한다 (방 정보는 RoomRepository).
@Component
public class RoomSessionRegistry {
    // roomCode → (playerId → session). 연결이 동시에 들고 나므로 안팎 모두 ConcurrentHashMap
    private final Map<String, Map<String, WebSocketSession>> rooms = new ConcurrentHashMap<>();

    // 새 연결을 방에 등록한다. 방이 없으면 만든다.
    // 세션은 동시 전송에 안전하도록 Decorator로 감싸서 저장한다 (전송 제한 10초, 버퍼 512KB).
    // 같은 playerId가 이미 있으면 새 세션으로 교체한다 (재접속).
    public void register(String roomCode, String playerId, WebSocketSession session) {
        WebSocketSession decoratedSession =
                new ConcurrentWebSocketSessionDecorator(session, 10000, 512 * 1024);
        rooms.compute(roomCode, (k, players) -> {
            if (players == null) {
                players = new ConcurrentHashMap<>();
            }
            players.put(playerId, decoratedSession);
            return players;
        });
    }

    // 연결이 끊긴 플레이어를 방에서 지운다. 방이 비면 방도 지운다.
    // 저장된 세션과 id가 같을 때만 지운다:
    // 재접속 후 옛 연결의 종료 이벤트가 늦게 와도 새 연결이 지워지지 않게 하기 위함.
    public void unregister(String roomCode, String playerId, WebSocketSession session) {
        rooms.computeIfPresent(roomCode, (k, players) -> {
            WebSocketSession currentSession = players.get(playerId);
            if (currentSession != null && currentSession.getId().equals(session.getId())) {
                players.remove(playerId);
            }
            return players.isEmpty() ? null : players;
        });
    }

    // 방에 연결된 세션 목록. 방이 없으면 빈 리스트.
    // 복사본을 돌려주므로 받은 쪽에서 수정해도 명부에는 영향이 없다.
    public List<WebSocketSession> getSessions(String roomCode) {
        Map<String, WebSocketSession> sessions = rooms.get(roomCode);
        return sessions == null ? List.of() : List.copyOf(sessions.values());
    }

    // 특정 플레이어의 세션. 방이나 플레이어가 없으면 Optional.empty().
    public Optional<WebSocketSession> find(String roomCode, String playerId) {
        Map<String, WebSocketSession> sessions = rooms.get(roomCode);
        return sessions == null ? Optional.empty() : Optional.ofNullable(sessions.get(playerId));
    }
}
