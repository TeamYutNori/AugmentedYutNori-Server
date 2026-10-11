package com.teamyutnori.yutnori.game.augment.effect;

import com.teamyutnori.yutnori.game.augment.AugmentIds;
import com.teamyutnori.yutnori.game.board.AugmentContext;
import com.teamyutnori.yutnori.game.board.BoardNode;
import com.teamyutnori.yutnori.game.board.BoardPath;

import java.util.List;

// 마라토너: 지름길로 갈 수 없는 대신 앞으로 갈 때 1칸 더 (Unity MarathonerAugment)
public class MarathonerAugment extends TeamAugment {

    public MarathonerAugment(int ownerTeam) { super(ownerTeam); }

    @Override
    public String id() { return AugmentIds.MARATHONER; }

    @Override
    public int modifyMoveCount(AugmentContext context, int moveCount) {
        if (!context.isMine(this)) return moveCount;
        if (moveCount <= 0) return moveCount;   // 빽도는 그대로
        return moveCount + 1;
    }

    @Override
    public void modifyPaths(AugmentContext context, List<BoardPath> paths) {
        if (!context.isMine(this)) return;

        // 지름길로 시작하는 경로 제거
        BoardNode start = context.getPiece().getNode();
        paths.removeIf(path -> !path.nodes().isEmpty() && start.getShortcutNodes().contains(path.nodes().get(0)));

        // 원래 길 경로가 없으면 추가 (지름길만 있던 모서리에 멈춘 말도 움직일 수 있게)
        BoardNode normalFirst = start.getNext(context.getPiece().getCameFrom());
        boolean hasNormal = paths.stream().anyMatch(path -> !path.nodes().isEmpty() && path.nodes().get(0) == normalFirst);
        if (!hasNormal && context.getMoveCount() > 0) {
            BoardPath normal = context.getGraph().getNormalPath(start, context.getPiece().getCameFrom(), context.getMoveCount());
            if (normal != null) paths.add(normal);
        }
    }
}
