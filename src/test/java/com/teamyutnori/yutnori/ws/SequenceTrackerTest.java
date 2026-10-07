package com.teamyutnori.yutnori.ws;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// 방별 서버 송신 순번이 1부터 증가하고, 방끼리 독립이며, remove 후 초기화되는지 검증한다
class SequenceTrackerTest {

    private final SequenceTracker tracker = new SequenceTracker();

    @Test
    void 순번은_1부터_1씩_증가한다() {
        assertEquals(1, tracker.next("ABC123"));
        assertEquals(2, tracker.next("ABC123"));
        assertEquals(3, tracker.next("ABC123"));
    }

    @Test
    void 방마다_순번이_따로_간다() {
        tracker.next("ABC123");
        tracker.next("ABC123");

        assertEquals(1, tracker.next("XYZ789"));
        assertEquals(3, tracker.next("ABC123"));
    }

    @Test
    void remove하면_다시_1부터_시작한다() {
        tracker.next("ABC123");
        tracker.next("ABC123");

        tracker.remove("ABC123");

        assertEquals(1, tracker.next("ABC123"));
    }
}