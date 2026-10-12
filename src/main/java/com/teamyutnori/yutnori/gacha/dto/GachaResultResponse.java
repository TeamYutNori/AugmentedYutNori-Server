package com.teamyutnori.yutnori.gacha.dto;

// POST /gacha/draw 응답
//  - iconId    : 뽑힌 아이콘 id (Unity Resources/ProfileIcons/{iconId}.png)
//  - duplicate : 이미 가지고 있던 아이콘이면 true (보상 없음)
//  - count     : 이 아이콘을 뽑은 총 횟수 (처음이면 1)
//  - gold      : 차감 후 보유 골드
//  - price     : 이번에 쓴 골드
public record GachaResultResponse(
        String iconId,
        boolean duplicate,
        int count,
        int gold,
        int price
) {
}
