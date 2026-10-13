package com.teamyutnori.yutnori.achievement;

// achievements.json의 업적 한 개. 필드 이름은 json 키와 같아야 한다
//  - id          : 업적 식별자 (예: "games_5")
//  - name        : 화면 표시 이름
//  - description : 화면 표시 설명 (예: "게임 5판 플레이")
//  - stat        : 세는 횟수 종류
//  - goal        : 달성 기준 횟수
//  - rewardGold  : 보상 수령 시 받는 골드
public record AchievementDefinition(String id, String name, String description,
                                    AchievementStat stat, int goal, int rewardGold) {}
