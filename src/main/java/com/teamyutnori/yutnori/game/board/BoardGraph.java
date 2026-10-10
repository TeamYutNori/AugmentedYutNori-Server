package com.teamyutnori.yutnori.game.board;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 판 모양 + 이동 경로 계산 (Unity BoardGraph 이식)
// 게임마다 BoardLayoutRepository.create()로 새로 만든다 (판 객체를 게임끼리 같이 쓰지 않음)
// Unity의 Rebuild(게임 중 판 모양 바꾸기)는 아직 서버에서 쓰지 않아 옮기지 않았다
public class BoardGraph {

    private final Map<Integer, BoardNode> nodes = new LinkedHashMap<>();
    private final BoardNode startNode;   // 판 밖 출발 칸. 앞으로 가다가 이 칸에 들어오면 골인

    // 모서리/방에 멈췄을 때 지름길 말고 원래 길도 고를 수 있게 할지 (GameSetup.allowSkipShortcut)
    private boolean allowSkipShortcut;

    private BoardGraph(BoardDefinition definition) {
        definition.validate();

        for (NodeDefinition def : definition.nodes()) {
            nodes.put(def.id(), new BoardNode(def.id(), def.type()));
        }
        for (NodeDefinition def : definition.nodes()) {
            BoardNode node = nodes.get(def.id());
            for (int id : def.next())     node.nextNodes.add(nodes.get(id));
            for (int id : def.prev())     node.prevNodes.add(nodes.get(id));
            for (int id : def.shortcut()) node.shortcutNodes.add(nodes.get(id));
        }
        this.startNode = nodes.get(definition.startNodeId());
    }

    public static BoardGraph from(BoardDefinition definition) {
        return new BoardGraph(definition);
    }

    // 네트워크로 받은 칸 번호로 칸을 찾는다. 없으면 null
    public BoardNode getNode(int id) { return nodes.get(id); }

    public BoardNode getStartNode()             { return startNode; }
    public Collection<BoardNode> getNodes()     { return Collections.unmodifiableCollection(nodes.values()); }
    public boolean isAllowSkipShortcut()        { return allowSkipShortcut; }
    public void setAllowSkipShortcut(boolean v) { this.allowSkipShortcut = v; }

    // start: 말의 현재 칸 (판에 안 나온 말은 startNode)
    // cameFrom: 말이 직전에 있던 칸 (판에 안 나온 말은 null)
    // moveCount: 도=1 ~ 모=5, 빽도=-1 (증강으로 더 커지거나 여러 칸 뒤로 갈 수도 있음)
    public List<BoardPath> getMovePaths(BoardNode start, BoardNode cameFrom, int moveCount) {
        List<BoardPath> result = new ArrayList<>();
        if (start == null || moveCount == 0) return result;

        if (moveCount < 0) {
            BoardPath back = moveBackward(start, cameFrom, -moveCount);
            if (back != null) result.add(back);
            return result;
        }

        // 첫 걸음 후보: 멈춘 칸의 지름길 (+ 옵션에 따라 원래 길)
        List<BoardNode> firstSteps = new ArrayList<>(start.shortcutNodes);
        if (firstSteps.isEmpty() || allowSkipShortcut) {
            BoardNode normal = start.getNext(cameFrom);
            if (normal != null && !firstSteps.contains(normal)) firstSteps.add(normal);
        }

        for (BoardNode first : firstSteps) {
            result.add(moveForward(start, cameFrom, first, moveCount));
        }
        return result;
    }

    private BoardPath moveForward(BoardNode start, BoardNode cameFrom, BoardNode first, int moveCount) {
        List<BoardNode> path = new ArrayList<>();
        BoardNode prev = cameFrom;
        BoardNode current = start;
        BoardNode next = first;

        for (int i = 0; i < moveCount && next != null; i++) {
            prev = current;
            current = next;
            path.add(current);

            // END를 지나 START로 들어오면 남은 칸과 상관없이 골인
            if (current == startNode) return new BoardPath(start, path, true, false, prev);

            next = current.getNext(prev);
        }
        return new BoardPath(start, path, false, false, prev);
    }

    private BoardPath moveBackward(BoardNode start, BoardNode cameFrom, int moveCount) {
        // 판에 안 나온 말은 뒤로 움직일 수 없음
        if (start == startNode) return null;

        List<BoardNode> path = new ArrayList<>();
        BoardNode ahead = null;   // 뒤로 가기 직전에 있던 칸 (앞쪽 칸)
        BoardNode current = start;

        // 첫 걸음: 직전 칸이 이 칸의 prev에 있으면 왔던 길로 되돌아간다 (START에서 막 나온 말은 prev[0] = END로)
        BoardNode back = cameFrom != null && current.prevNodes.contains(cameFrom) ? cameFrom : getBack(current, null);

        for (int i = 0; i < moveCount && back != null; i++) {
            ahead = current;
            current = back;
            path.add(current);
            back = getBack(current, ahead);
        }

        if (path.isEmpty()) return null;

        // 도착 칸에서 다음에 앞으로 갈 때 쓸 직전 칸 = 한 칸 더 뒤로 갔다면 갔을 칸
        BoardNode backCameFrom = current == startNode ? null : back;
        return new BoardPath(start, path, false, true, backCameFrom);
    }

    // 뒤로 한 칸. next[j] 쪽에서 뒤로 들어왔으면 prev[j]로 나간다 (앞으로 갈 때 짝의 역방향). 짝이 없으면 prev[0]
    private static BoardNode getBack(BoardNode node, BoardNode ahead) {
        if (node.prevNodes.isEmpty()) return null;

        int index = ahead != null ? node.nextNodes.indexOf(ahead) : -1;
        return index >= 0 && index < node.prevNodes.size() ? node.prevNodes.get(index) : node.prevNodes.get(0);
    }
}
