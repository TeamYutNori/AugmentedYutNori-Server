package com.teamyutnori.yutnori.game.board;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// 판의 칸 하나 (Unity BoardNode). 같은 판 안에서는 객체 자체로 비교한다 (==)
public class BoardNode {

    private final int id;              // 고정 번호. 네트워크로 칸을 가리킬 때 사용
    private final BoardNodeType type;

    // prevNodes[i]에서 들어온 말은 nextNodes[i]로 나간다. 짝이 없으면 nextNodes[0]
    final List<BoardNode> nextNodes = new ArrayList<>();
    final List<BoardNode> prevNodes = new ArrayList<>();
    final List<BoardNode> shortcutNodes = new ArrayList<>(); // 이 칸에 멈췄을 때만 쓰는 길

    BoardNode(int id, BoardNodeType type) {
        this.id = id;
        this.type = type;
    }

    public int getId()             { return id; }
    public BoardNodeType getType() { return type; }

    public List<BoardNode> getNextNodes()     { return Collections.unmodifiableList(nextNodes); }
    public List<BoardNode> getPrevNodes()     { return Collections.unmodifiableList(prevNodes); }
    public List<BoardNode> getShortcutNodes() { return Collections.unmodifiableList(shortcutNodes); }

    // 들어온 방향을 기준으로 다음 칸을 고른다. 갈 곳이 없으면 null
    public BoardNode getNext(BoardNode cameFrom) {
        if (nextNodes.isEmpty()) return null;

        int index = cameFrom != null ? prevNodes.indexOf(cameFrom) : -1;
        return index >= 0 && index < nextNodes.size() ? nextNodes.get(index) : nextNodes.get(0);
    }

    @Override
    public String toString() { return "Node" + id + "(" + type + ")"; }
}
