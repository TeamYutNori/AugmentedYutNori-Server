package com.teamyutnori.yutnori.room.model;

// 방의 현재 상태
public enum RoomStatus {

    // 참가자를 기다리는 상태
    WAITING,

    // 게임이 진행 중인 상태
    PLAYING,

    // 게임이 종료된 상태
    FINISHED
}