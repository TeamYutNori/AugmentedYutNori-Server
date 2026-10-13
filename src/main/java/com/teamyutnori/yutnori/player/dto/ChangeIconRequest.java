package com.teamyutnori.yutnori.player.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeIconRequest(
        @NotBlank
        @Size(max = 64)
        String iconId
) {
}