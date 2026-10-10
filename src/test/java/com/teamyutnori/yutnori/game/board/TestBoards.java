package com.teamyutnori.yutnori.game.board;

import tools.jackson.databind.json.JsonMapper;

// 테스트용 판. 실제 resources/boards/*.json을 그대로 읽는다 (서버와 같은 판으로 테스트)
public final class TestBoards {
    private TestBoards() {}

    private static final BoardLayoutRepository REPOSITORY = new BoardLayoutRepository(JsonMapper.builder().build());

    public static BoardLayoutRepository repository() { return REPOSITORY; }

    // 게임마다 새 판이 필요하므로 매번 새로 만든다
    public static BoardGraph defaultBoard() { return REPOSITORY.create(BoardLayoutRepository.DEFAULT_ID, false); }
}
