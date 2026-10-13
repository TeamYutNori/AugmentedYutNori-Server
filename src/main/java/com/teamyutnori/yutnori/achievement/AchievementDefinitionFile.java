package com.teamyutnori.yutnori.achievement;

import java.util.List;

// achievements.json 전체 구조
// {
//   "version": 1,
//   "achievements": [ { "id": ..., "name": ..., "description": ..., "stat": ..., "goal": ..., "rewardGold": ... }, ... ]
// }
public record AchievementDefinitionFile(int version, List<AchievementDefinition> achievements) {}
