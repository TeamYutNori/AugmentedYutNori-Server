package com.teamyutnori.yutnori.achievement.dto;

// 업적 한 줄
//  - progress  : 지금까지 횟수 (goal을 넘으면 goal로 표시)
//  - achieved  : 달성했는지 (보상 받기 버튼 표시)
//  - claimed   : 보상을 이미 받았는지
public record AchievementResponse(
        String id,
        String name,
        String description,
        String stat,
        int goal,
        int progress,
        int rewardGold,
        boolean achieved,
        boolean claimed
) {}
