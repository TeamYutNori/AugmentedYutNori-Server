package com.teamyutnori.yutnori.game.board;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BoardLayoutRepositoryTest {

    private final BoardLayoutRepository repository = TestBoards.repository();

    @Test
    @DisplayName("Unity에서 옮긴 판 5개를 모두 읽는다")
    void loadsAllBoards() {
        for (String id : new String[] {"default", "clockwise", "flipped", "half", "long-diagonal"}) {
            assertTrue(repository.exists(id), id + " 판이 없음");
        }
        assertFalse(repository.exists("nope"));
        assertFalse(repository.exists(null));
    }

    @Test
    @DisplayName("기본 판: 칸 30개, 출발 칸은 0번 START")
    void defaultBoard() {
        BoardGraph graph = repository.create("default", false);
        assertEquals(30, graph.getNodes().size());
        assertEquals(0, graph.getStartNode().getId());
        assertEquals(BoardNodeType.START, graph.getStartNode().getType());
    }

    @Test
    @DisplayName("create는 게임마다 새 판을 만든다")
    void createsNewGraphEachTime() {
        assertNotSame(repository.create("default", false), repository.create("default", false));
        assertTrue(repository.create("default", true).isAllowSkipShortcut());
    }

    @Test
    @DisplayName("없는 판 id면 예외")
    void unknownId() {
        assertThrows(IllegalArgumentException.class, () -> repository.create("nope", false));
    }

    @Test
    @DisplayName("칸 종류는 문자열·Unity 숫자 둘 다 읽는다")
    void nodeTypeParsing() {
        assertEquals(BoardNodeType.START, BoardNodeType.from("Start"));
        assertEquals(BoardNodeType.CORNER, BoardNodeType.from("CORNER"));
        assertEquals(BoardNodeType.CENTER, BoardNodeType.from(4));
    }
}
