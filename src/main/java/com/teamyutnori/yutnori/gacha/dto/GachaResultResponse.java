package com.teamyutnori.yutnori.gacha.dto;

import java.util.List;

// POST /gacha/draw?count=N 응답
//  - results   : 뽑은 순서대로 N개 (1회면 1개, 10연차면 10개)
//  - gold      : 차감 후 보유 골드
//  - price     : 이번에 쓴 골드 (PRICE × N)
//  - iconId / duplicate / count : 첫 번째 결과와 같다. 1회 뽑기만 아는 예전 클라 호환용
public record GachaResultResponse(
        String iconId,
        boolean duplicate,
        int count,
        int gold,
        int price,
        List<GachaDrawResult> results
) {
    public static GachaResultResponse of(List<GachaDrawResult> results, int gold, int price) {
        GachaDrawResult first = results.get(0);
        return new GachaResultResponse(first.iconId(), first.duplicate(), first.count(), gold, price, List.copyOf(results));
    }
}
