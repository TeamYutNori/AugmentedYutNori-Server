package com.teamyutnori.yutnori.game.yut;

// 테스트용 난수: 넣어 둔 값을 차례대로 돌려준다 (Unity SequenceRandomProvider와 동일)
// 예) new SequenceRandomProvider(0.9, 0.9, 0.9, 0.9) → 막대 4개 모두 둥근 면 → 모
//     (FLAT_CHANCE 0.5 미만이면 평평한 면, 이상이면 둥근 면)
public class SequenceRandomProvider implements RandomProvider {
    private final double[] values;
    private int index;

    public SequenceRandomProvider(double... values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("값을 최소 1개 넣어야 합니다.");
        }
        this.values = values;
    }

    @Override
    public double nextDouble() {
        return values[index++ % values.length];   // 끝까지 쓰면 처음부터 다시
    }
}
