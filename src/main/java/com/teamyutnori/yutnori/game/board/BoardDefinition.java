package com.teamyutnori.yutnori.game.board;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

// 판 구성 데이터 (칸 번호/종류/연결만, 위치 없음). resources/boards/*.json 한 파일 = 판 하나
// Unity BoardDefinition과 같은 구조라, Unity BoardLayoutSO의 definition을 그대로 json으로 옮기면 된다
public record BoardDefinition(int startNodeId, List<NodeDefinition> nodes) {

    public BoardDefinition {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
    }

    public NodeDefinition find(int id) {
        for (NodeDefinition node : nodes) if (node.id() == id) return node;
        return null;
    }

    // 번호 중복, 없는 칸으로의 연결, 시작 칸(START 종류, 판에 하나)을 검사한다 (Unity Validate와 같은 규칙)
    public void validate() {
        Set<Integer> ids = new HashSet<>();
        int startCount = 0;
        for (NodeDefinition node : nodes) {
            if (node.id() < 0) throw new IllegalStateException("칸 번호가 음수입니다: " + node.id());
            if (!ids.add(node.id())) throw new IllegalStateException("칸 번호가 겹칩니다: " + node.id());
            if (node.type() == BoardNodeType.START) startCount++;
        }

        if (startCount != 1)
            throw new IllegalStateException("종류가 START인 칸이 " + startCount + "개입니다. 판에 하나만 있어야 합니다.");

        NodeDefinition start = find(startNodeId);
        if (start == null) throw new IllegalStateException("시작 칸(" + startNodeId + ")이 없습니다.");
        if (start.type() != BoardNodeType.START)
            throw new IllegalStateException("시작 칸(" + startNodeId + ")의 종류가 START가 아니라 " + start.type() + "입니다.");

        for (NodeDefinition node : nodes) {
            for (List<Integer> links : List.of(node.next(), node.prev(), node.shortcut())) {
                for (int id : links) {
                    if (!ids.contains(id))
                        throw new IllegalStateException(node.id() + "번 칸이 없는 칸(" + id + ")과 연결돼 있습니다.");
                }
            }
        }
    }
}
