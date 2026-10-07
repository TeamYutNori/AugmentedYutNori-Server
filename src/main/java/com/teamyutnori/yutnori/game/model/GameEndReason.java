package com.teamyutnori.yutnori.game.model;

// 게임 종료 이유. Unity GameEndReason과 이름이 같아야 한다
public enum GameEndReason {
    FINISHED,       // 말이 모두 들어옴
    FORFEIT,        // 기권 / 방 나감
    DISCONNECTED    // 재접속 유예시간 초과
}
