package com.teamyutnori.yutnori.achievement;

// 업적이 세는 횟수 종류. achievements.json의 "stat" 값과 이름이 같아야 한다
public enum AchievementStat {
    LOGIN_DAYS,     // 접속한 날 수 (하루에 여러 번 로그인해도 1)
    GAMES_PLAYED,   // 끝까지 플레이한 게임 수 (중간에 나간 게임은 제외)
    GACHA_DRAWS,    // 뽑기 횟수
    FIRST_PLACES    // 1등 한 횟수
}
