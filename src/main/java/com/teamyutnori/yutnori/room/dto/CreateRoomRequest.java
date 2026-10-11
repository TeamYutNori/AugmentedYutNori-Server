package com.teamyutnori.yutnori.room.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

// 방 생성 요청 DTO
public record CreateRoomRequest(
        // 공개방 여부
        boolean isPublic,

        // 방 최대 인원
        @Min(value = 2, message = "방 인원은 2명 이상이어야 합니다.")
        @Max(value = 4, message = "방 인원은 4명 이하여야 합니다.")
        int maxPlayers,

        // 제한 시간(초)
        @Min(value = 1, message = "제한 시간은 1초 이상이어야 합니다.")
        int timeLimit
) {
}