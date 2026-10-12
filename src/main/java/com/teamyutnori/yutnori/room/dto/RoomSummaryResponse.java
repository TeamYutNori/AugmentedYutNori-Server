package com.teamyutnori.yutnori.room.dto;

import com.teamyutnori.yutnori.room.model.Room;
import com.teamyutnori.yutnori.room.model.RoomStatus;

// 방 정보 응답 DTO
public record RoomSummaryResponse(
        String code,
        boolean isPublic,
        int currentPlayers,
        int maxPlayers,
        int timeLimit,
        RoomStatus status
) {

    // Room 객체를 응답 DTO로 변환
    public static RoomSummaryResponse from(Room room) {
        return new RoomSummaryResponse(
                room.getCode(),
                room.isPublic(),
                room.getCurrentPlayers(),
                room.getMaxPlayers(),
                room.getTimeLimit(),
                room.getStatus()
        );
    }
}