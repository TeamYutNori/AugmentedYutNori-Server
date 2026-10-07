package com.teamyutnori.yutnori.game.repository;

import com.teamyutnori.yutnori.game.model.GameSession;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class GameSessionRepository {
    private final Map<String, GameSession> sessions = new ConcurrentHashMap<>();

    public void save(GameSession session)            { sessions.put(session.getRoomCode(), session); }
    public Optional<GameSession> find(String roomCode) { return Optional.ofNullable(sessions.get(roomCode)); }
    public void delete(String roomCode)              { sessions.remove(roomCode); }
}