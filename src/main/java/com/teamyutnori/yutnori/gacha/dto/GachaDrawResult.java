package com.teamyutnori.yutnori.gacha.dto;

// 뽑기 한 번의 결과
//  - iconId    : 뽑힌 아이콘 id (Unity Resources/ProfileIcons/{iconId}.png)
//  - duplicate : 이미 가지고 있던 아이콘이면 true (보상 없음). 같은 10연차 안에서 두 번째로 나온 것도 true
//  - count     : 이 결과까지 반영한 보유 횟수 (처음이면 1)
public record GachaDrawResult(
        String iconId,
        boolean duplicate,
        int count
) {
}
