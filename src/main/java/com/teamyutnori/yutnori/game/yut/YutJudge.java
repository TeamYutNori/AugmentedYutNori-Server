package com.teamyutnori.yutnori.game.yut;

import org.springframework.stereotype.Component;

// 윷가락 4개 판정 (Unity StandardGetResult와 반드시 같은 결과여야 함)
@Component
public class YutJudge {
    public static final int STICK_COUNT = 4;
    public static final int MARKED_STICK = 0;   // 표시된 윷 = 첫 번째

    // sticks: true = 평평한 면
    public YutResult judge(boolean[] sticks) {
        if (sticks.length != STICK_COUNT) {
            throw new IllegalArgumentException(
                    "기본 규칙은 윷가락 " + STICK_COUNT + "개만 판정합니다. 받은 개수: " + sticks.length);
        }

        int flatCount = 0;
        for (boolean isFlat : sticks) {
            if (isFlat) flatCount++;
        }

        return switch (flatCount) {
            case 0 -> YutResult.Mo;
            case 1 -> sticks[MARKED_STICK] ? YutResult.BackDo : YutResult.Do;
            case 2 -> YutResult.Gae;
            case 3 -> YutResult.Geol;
            case 4 -> YutResult.Yut;
            default -> throw new IllegalStateException("판정할 수 없는 평평한 면 개수: " + flatCount);
        };
    }
}