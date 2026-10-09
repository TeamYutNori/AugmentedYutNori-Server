package com.teamyutnori.yutnori.game.board;

import java.util.List;

// 한 번의 이동 경로 (Unity BoardPath)
public record BoardPath(
        BoardNode start,
        List<BoardNode> nodes,   // 지나가는 칸 순서 (시작 칸 제외, 마지막이 도착 칸)
        boolean finished,        // END(참먹이)를 지나 START로 들어와 골인
        boolean backward,        // 빽도
        BoardNode cameFrom       // 도착 후 말이 기억할 직전 칸. 빽도로 갈림길에 도착하면 알 수 없어서 null
) {
    public BoardPath {
        nodes = List.copyOf(nodes);
    }

    public BoardNode destination() {
        return nodes.isEmpty() ? start : nodes.get(nodes.size() - 1);
    }
}
