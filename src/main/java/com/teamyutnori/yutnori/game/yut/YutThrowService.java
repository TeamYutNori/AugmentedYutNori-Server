package com.teamyutnori.yutnori.game.yut;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 막대 굴리기 + 판정 (Unity YutThrower.Throw)
@Component
@RequiredArgsConstructor
public class YutThrowService {
    private static final double FLAT_CHANCE = 0.5;

    private final RandomProvider random;   // 실행 시 SecureRandomProvider가 주입됨
    private final YutJudge judge;

    public YutThrowOutcome throwYut() {
        boolean[] sticks = new boolean[YutJudge.STICK_COUNT];
        for (int i = 0; i < sticks.length; i++) {
            sticks[i] = random.nextDouble() < FLAT_CHANCE;
        }
        return new YutThrowOutcome(sticks, judge.judge(sticks));
    }
}