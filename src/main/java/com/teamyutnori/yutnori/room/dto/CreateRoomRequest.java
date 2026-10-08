package com.teamyutnori.yutnori.room.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

// 방 생성 요청 DTO
public record CreateRoomRequest(

        // 방 이름
        @NotBlank(message = "방 이름은 비어 있을 수 없습니다.")
        String roomName,

        // 최대 참가 인원
        @Min(value = 2, message = "최소 2명 이상이어야 합니다.")
        @Max(value = 4, message = "최대 4명까지 가능합니다.")
        int maxPlayers
) {
}