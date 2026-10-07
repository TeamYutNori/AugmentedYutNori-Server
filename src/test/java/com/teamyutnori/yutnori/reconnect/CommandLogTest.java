package com.teamyutnori.yutnori.reconnect;

import com.teamyutnori.yutnori.reconnect.CommandLog.Entry;
import com.teamyutnori.yutnori.ws.dto.MessageType;
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

    // 받은 seq 그대로 기록하고, lastSeq는 마지막 seq를 돌려준다
    @Test
    void append() {
        Entry entry = log.append(ROOM, 3, MessageType.THROW_RESULT, 0, "gae");

        assertThat(entry.seq()).isEqualTo(3);
        assertThat(entry.type()).isEqualTo(MessageType.THROW_RESULT);
        assertThat(log.lastSeq(ROOM)).isEqualTo(3);
    }

    // seq 사이가 비어 있어도 (ROOM_STATE 등 기록 안 한 broadcast) 순서대로 읽힌다
    @Test
    void gap() {
        log.append(ROOM, 2, MessageType.THROW_RESULT, 0, "a");
        log.append(ROOM, 5, MessageType.MOVE_APPLIED, 0, "b");
        log.append(ROOM, 6, MessageType.TURN_CHANGED, CommandLog.SYSTEM_TEAM, "c");

        assertThat(log.readAll(ROOM)).extracting(Entry::seq).containsExactly(2L, 5L, 6L);
    }

    // readAfter(n)은 seq가 n보다 큰 것만 돌려준다 (n이 기록에 없는 번호여도 됨)
    @Test
    void readAfter() {
        log.append(ROOM, 2, MessageType.THROW_RESULT, 0, "a");
        log.append(ROOM, 5, MessageType.MOVE_APPLIED, 0, "b");
        log.append(ROOM, 6, MessageType.TURN_CHANGED, CommandLog.SYSTEM_TEAM, "c");

        assertThat(log.readAfter(ROOM, 2)).extracting(Entry::seq).containsExactly(5L, 6L);
        assertThat(log.readAfter(ROOM, 3)).extracting(Entry::seq).containsExactly(5L, 6L);
    }

    // 범위 밖 seq, 없는 방이어도 예외 없이 처리한다
    @Test
    void readAfterOutOfRange() {
        log.append(ROOM, 1, MessageType.THROW_RESULT, 0, "a");

        assertThat(log.readAfter(ROOM, -5)).hasSize(1);
        assertThat(log.readAfter(ROOM, 99)).isEmpty();
        assertThat(log.readAll("NONE")).isEmpty();
        assertThat(log.lastSeq("NONE")).isZero();
    }

    // 방마다 따로 기록된다
    @Test
    void separateRoom() {
        log.append("ROOM1", 1, MessageType.THROW_RESULT, 0, "a");
        log.append("ROOM2", 1, MessageType.THROW_RESULT, 0, "b");

        assertThat(log.readAll("ROOM1")).hasSize(1);
        assertThat(log.readAll("ROOM2")).hasSize(1);
    }

    // seq가 이전 기록보다 작거나 같으면 예외 (서버 버그)
    @Test
    void seqNotIncreasing() {
        log.append(ROOM, 5, MessageType.THROW_RESULT, 0, "a");

        assertThatThrownBy(() -> log.append(ROOM, 5, MessageType.MOVE_APPLIED, 0, "b"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> log.append(ROOM, 3, MessageType.MOVE_APPLIED, 0, "b"))
                .isInstanceOf(IllegalStateException.class);
    }

    // seq가 0 이하이거나 type이 null이면 예외
    @Test
    void invalidArgs() {
        assertThatThrownBy(() -> log.append(ROOM, 0, MessageType.THROW_RESULT, 0, "a"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> log.append(ROOM, 1, null, 0, "a"))
                .isInstanceOf(NullPointerException.class);
    }

    // 돌려준 목록은 수정할 수 없다 (원본 로그 보호)
    @Test
    void readOnly() {
        log.append(ROOM, 1, MessageType.THROW_RESULT, 0, "a");
        List<Entry> all = log.readAll(ROOM);

        assertThatThrownBy(() -> all.add(new Entry(9, MessageType.ERROR, 0, null)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // clear 후에는 기록이 비고, 다시 1부터 기록할 수 있다
    @Test
    void clear() {
        log.append(ROOM, 7, MessageType.THROW_RESULT, 0, "a");
        log.clear(ROOM);

        assertThat(log.lastSeq(ROOM)).isZero();
        assertThat(log.append(ROOM, 1, MessageType.THROW_RESULT, 0, "b").seq()).isEqualTo(1);
    }
}