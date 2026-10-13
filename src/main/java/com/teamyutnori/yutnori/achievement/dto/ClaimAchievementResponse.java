package com.teamyutnori.yutnori.achievement.dto;

// POST /players/me/achievements/{id}/claim 응답
//  - rewardGold : 이번에 받은 골드
//  - gold       : 받은 뒤 보유 골드
public record ClaimAchievementResponse(String achievementId, int rewardGold, int gold) {}
