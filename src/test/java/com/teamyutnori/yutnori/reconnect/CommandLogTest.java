package com.teamyutnori.yutnori.reconnect;

import com.teamyutnori.yutnori.reconnect.CommandLog.Entry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommandLogTest {

    private static final String ROOM = "ABCD";
    private CommandLog log;

    @BeforeEach
    void setUp() {
        log = new CommandLog();
    }

    // seq는 1부터 순서대로 붙고, lastSeq는 마지막 번호를 돌려준다
    @Test
    void seqOrder() {
        Entry first = log.append(ROOM, "THROW_RESULT", 0, "gae");
        Entry second = log.append(ROOM, "MOVE_APPLIED", 0, "move");

        assertThat(first.seq()).isEqualTo(1);
        assertThat(second.seq()).isEqualTo(2);
        assertThat(log.lastSeq(ROOM)).isEqualTo(2);
    }

    // readAfter(n)은 n번 다음부터 끝까지만 돌려준다 (재접속 시 놓친 명령)
    @Test
    void readAfter() {
        log.append(ROOM, "THROW_RESULT", 0, "a");
        log.append(ROOM, "MOVE_APPLIED", 0, "b");
        log.append(ROOM, "TURN_CHANGED", CommandLog.SYSTEM_TEAM, "c");

        List<Entry> after = log.readAfter(ROOM, 1);

        assertThat(after).extracting(Entry::seq).containsExactly(2L, 3L);
    }

    // 음수, 범위 밖 seq, 없는 방이어도 예외 없이 처리한다
    @Test
    void readAfterOutOfRange() {
        log.append(ROOM, "THROW_RESULT", 0, "a");

        assertThat(log.readAfter(ROOM, -5)).hasSize(1);
        assertThat(log.readAfter(ROOM, 99)).isEmpty();
        assertThat(log.readAll("NONE")).isEmpty();
    }

    // 방마다 seq가 따로 1부터 붙는다
    @Test
    void separateRoom() {
        log.append("ROOM1", "THROW_RESULT", 0, "a");
        Entry other = log.append("ROOM2", "THROW_RESULT", 0, "b");

        assertThat(other.seq()).isEqualTo(1);
    }

    // 돌려준 목록은 수정할 수 없다 (원본 로그 보호)
    @Test
    void readOnly() {
        log.append(ROOM, "THROW_RESULT", 0, "a");
        List<Entry> all = log.readAll(ROOM);

        assertThatThrownBy(() -> all.add(new Entry(9, "X", 0, null)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // clear 후에는 로그가 비고 seq가 다시 1부터 시작한다
    @Test
    void clear() {
        log.append(ROOM, "THROW_RESULT", 0, "a");
        log.clear(ROOM);

        assertThat(log.lastSeq(ROOM)).isZero();
        assertThat(log.append(ROOM, "THROW_RESULT", 0, "b").seq()).isEqualTo(1);
    }

    // type이 비어 있으면 예외
    @Test
    void blankType() {
        assertThatThrownBy(() -> log.append(ROOM, " ", 0, "a"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}