package com.teamyutnori.yutnori.achievement.dto;

import java.util.List;

// GET /players/me/achievements 응답 (achievements.json 순서)
public record AchievementListResponse(List<AchievementResponse> achievements) {}
