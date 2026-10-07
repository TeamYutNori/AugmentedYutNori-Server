package com.teamyutnori.yutnori.reconnect;

import java.util.Optional;

public interface GameEndPort {

    // 이 방이 게임 진행 중인지(로비, 이미 끝난 게임이면 false)
    boolean isPlaying(String roomCode);

    // 플레이어의 팀 번호. 모르면 empty
    Optional<Integer> findTeam(String roomCode, String playerId);

    // 게임 종료(phase = ENDED, winnerTeam 설정)
    void endGame(String roomCode, int winnerTeam);
}
