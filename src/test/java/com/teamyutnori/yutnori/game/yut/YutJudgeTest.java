package com.teamyutnori.yutnori.game.yut;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Unity StandardGetResult와 같은 판정인지 확인 (막대 4개의 16가지 조합 전부)
// Unity 쪽 판정 테스트와 같은 입력표를 쓰면 C#·Java 결과가 같은지 비교할 수 있다
class YutJudgeTest {

    private final YutJudge judge = new YutJudge();

    // Unity 규칙: 평평한 면 0개 모, 1개 도(표시 윷[0]이 평평하면 빽도), 2개 개, 3개 걸, 4개 윷
    private static YutResult expected(boolean[] sticks) {
        int flat = 0;
        for (boolean s : sticks) if (s) flat++;
        return switch (flat) {
            case 0 -> YutResult.Mo;
            case 1 -> sticks[0] ? YutResult.BackDo : YutResult.Do;
            case 2 -> YutResult.Gae;
            case 3 -> YutResult.Geol;
            default -> YutResult.Yut;
        };
    }

    @Test
    @DisplayName("막대 16가지 조합이 모두 Unity 규칙과 같은 결과")
    void allSixteenCombinations() {
        for (int mask = 0; mask < 16; mask++) {
            boolean[] sticks = new boolean[4];
            for (int i = 0; i < 4; i++) sticks[i] = (mask & (1 << i)) != 0;
            assertEquals(expected(sticks), judge.judge(sticks), "mask=" + mask);
        }
    }

    @Test
    @DisplayName("대표 사례: 빽도는 표시 윷만 평평할 때만")
    void backDoOnlyWhenMarkedStickIsFlat() {
        assertEquals(YutResult.BackDo, judge.judge(new boolean[]{true, false, false, false}));
        assertEquals(YutResult.Do, judge.judge(new boolean[]{false, true, false, false}));
        assertEquals(YutResult.Mo, judge.judge(new boolean[]{false, false, false, false}));
        assertEquals(YutResult.Yut, judge.judge(new boolean[]{true, true, true, true}));
    }

    @Test
    @DisplayName("막대가 4개가 아니면 예외")
    void wrongStickCount() {
        assertThrows(IllegalArgumentException.class, () -> judge.judge(new boolean[3]));
    }

    @Test
    @DisplayName("YutThrowService: 0.5 미만이면 평평한 면으로 굴린다")
    void throwServiceUsesRandom() {
        // 평평 / 둥근 / 둥근 / 둥근 → 표시 윷만 평평 → 빽도
        YutThrowService service = new YutThrowService(new SequenceRandomProvider(0.1, 0.9, 0.9, 0.9), judge);
        YutThrowOutcome outcome = service.throwYut();
        assertEquals(YutResult.BackDo, outcome.result());
        assertEquals(4, outcome.sticks().length);
    }
}
