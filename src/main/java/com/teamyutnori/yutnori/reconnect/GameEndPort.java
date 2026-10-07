package com.teamyutnori.yutnori.reconnect;

import java.util.Optional;

public interface GameEndPort {

    boolean isPlaying(String roomCode);

    Optional<Integer> findTeam(String roomCode, String playerId);

    void endGame(String roomCode, int winnerTeam);
}
