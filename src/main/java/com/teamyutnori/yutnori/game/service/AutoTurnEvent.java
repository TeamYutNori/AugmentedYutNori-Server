package com.teamyutnori.yutnori.game.service;

// 연결이 끊긴 팀(GameSession.isAuto)의 턴 제한시간이 끝났을 때 GameService가 발행한다
// AutoPlayService가 받아서 그 팀 대신 던지기·이동을 진행한다
public record AutoTurnEvent(String roomCode, int team) {}
