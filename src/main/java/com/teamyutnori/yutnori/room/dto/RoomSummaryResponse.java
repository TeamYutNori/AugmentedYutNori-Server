package com.teamyutnori.yutnori.room.dto;

import com.teamyutnori.yutnori.room.model.RoomStatus;

// 방 목록/조회용 요약 응답 DTO
public record RoomSummaryResponse(

        // 방 코드
        String roomCode,

        // 방 이름
        String roomName,

        // 현재 참가 인원
        int currentPlayers,

        // 최대 참가 인원
        int maxPlayers,

        // 현재 방 상태
        RoomStatus status
) {
}