package com.teamyutnori.yutnori.game.repository;

import com.teamyutnori.yutnori.game.model.GameSession;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// 진행 중인 게임 세션 저장소 (DB 없이 서버 메모리). key = roomCode
@Repository
public class GameSessionRepository {
    private final Map<String, GameSession> sessions = new ConcurrentHashMap<>();

    public void save(GameSession session)            { sessions.put(session.getRoomCode(), session); }

    // 같은 방 세션이 없을 때만 저장하고 true. 이미 있으면 저장하지 않고 false.
    // "있는지 확인 → 저장"을 따로 하면 동시에 두 번 불렸을 때 둘 다 통과할 수 있어서 한 번에 처리한다
    public boolean saveIfAbsent(GameSession session) {
        return sessions.putIfAbsent(session.getRoomCode(), session) == null;
    }
    public Optional<GameSession> find(String roomCode) { return Optional.ofNullable(sessions.get(roomCode)); }
    public void delete(String roomCode)              { sessions.remove(roomCode); }
}
