package com.teamyutnori.yutnori.game.board;

import java.util.List;

// 판 json의 칸 하나 (Unity NodeDefinition과 같은 구조)
// 연결 규칙: prev[i]에서 들어온 말은 next[i]로 나간다. 짝이 없으면 next[0]
public record NodeDefinition(
        int id,
        BoardNodeType type,
        List<Integer> next,      // 앞으로 갈 칸
        List<Integer> prev,      // 뒤로(빽도) 갈 칸
        List<Integer> shortcut   // 이 칸에 "멈췄을 때만" 들어가는 지름길
) {
    public NodeDefinition {
        if (type == null) type = BoardNodeType.NORMAL;
        next = next == null ? List.of() : List.copyOf(next);
        prev = prev == null ? List.of() : List.copyOf(prev);
        shortcut = shortcut == null ? List.of() : List.copyOf(shortcut);
    }
}
