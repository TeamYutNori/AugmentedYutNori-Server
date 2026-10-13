package com.teamyutnori.yutnori.player.dto;

import com.teamyutnori.yutnori.player.entity.Player;

public record PlayerProfileResponse(
        String nickname,
        String iconId,
        int gold
) {
    public static PlayerProfileResponse from(Player player) {
        return new PlayerProfileResponse(
                player.getNickname(),
                player.getSelectedIconId(),
                player.getGold()
        );
    }
}