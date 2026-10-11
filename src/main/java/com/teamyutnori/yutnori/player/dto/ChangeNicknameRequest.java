package com.teamyutnori.yutnori.player.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeNicknameRequest(
        @NotBlank(message = "닉네임은 비어 있을 수 없습니다.")
        @Size(max = 20, message = "닉네임은 20자 이하여야 합니다.")
        String nickname
) {
}