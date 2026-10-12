package com.teamyutnori.yutnori.game.service;

import com.teamyutnori.yutnori.game.board.BoardGraph;
import com.teamyutnori.yutnori.game.board.BoardNode;
import com.teamyutnori.yutnori.game.board.BoardPath;
import com.teamyutnori.yutnori.game.board.BoardState;
import com.teamyutnori.yutnori.game.board.Piece;
import com.teamyutnori.yutnori.game.yut.YutResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

// 자동 진행에서 둘 이동 하나 고르기 (같은 판이면 항상 같은 결과, 랜덤 없음)
//
// 후보는 BoardState.getMovePaths로 뽑는다 → 이동 증강(청개구리·잔걸음 강화 등)이 그대로 반영되고,
// 고른 이동은 GameService.doMove → BoardState.tryMove에서 그대로 통과한다
//
// 우선순위
//  1. 골인하는 이동
//  2. 상대 말을 잡는 이동 (도착 칸에 상대 말. 방패 같은 잡기 증강은 실제 이동 때 반영)
//  3. 앞으로 가는 이동 (빽도는 다른 수가 없을 때만)
//  4. 이동 후 골인까지 남은 칸이 가장 적은 말 = 가장 앞선 말
//  5. 같으면 말 번호가 작은 것, 칸 수가 큰 것
@Component
public class AutoMovePolicy {

    // 골인까지 남은 칸 계산 상한 (판 한 바퀴보다 충분히 큼)
    static final int MAX_DISTANCE = 64;

    public record AutoMove(int pieceId, int moveCount, int destinationNodeId,
                           boolean finishes, int captures, boolean backward, int remaining) {}

    private static final Comparator<AutoMove> ORDER = Comparator
            .comparing((AutoMove m) -> !m.finishes())
            .thenComparing(m -> -m.captures())
            .thenComparing(AutoMove::backward)
            .thenComparingInt(AutoMove::remaining)
            .thenComparingInt(AutoMove::pieceId)
            .thenComparing(m -> -m.moveCount())
            .thenComparingInt(AutoMove::destinationNodeId);

    public Optional<AutoMove> choose(BoardState board, int team, List<YutResult> storedResults) {
        return candidates(board, team, storedResults).stream().min(ORDER);
    }

    // 이 팀이 지금 저장된 윷 결과로 둘 수 있는 모든 이동
    public List<AutoMove> candidates(BoardState board, int team, List<YutResult> storedResults) {
        Set<Integer> moveCounts = new LinkedHashSet<>();
        for (YutResult result : storedResults) moveCounts.add(result.steps());

        List<AutoMove> moves = new ArrayList<>();
        for (Piece piece : board.getPiecesOf(team)) {
            if (piece.isFinished()) continue;
            for (int moveCount : moveCounts) {
                for (BoardPath path : board.getMovePaths(piece, moveCount)) {
                    BoardNode destination = path.destination();
                    moves.add(new AutoMove(
                            piece.getId(), moveCount, destination.getId(),
                            path.finished(),
                            path.finished() ? 0 : countOpponents(board, destination, team),
                            path.backward(),
                            path.finished() ? 0 : remaining(board.getGraph(), destination, path.cameFrom())));
                }
            }
        }
        return moves;
    }

    private static int countOpponents(BoardState board, BoardNode node, int team) {
        int count = 0;
        for (Piece other : board.getPiecesAt(node)) if (other.getTeam() != team) count++;
        return count;
    }

    // node에서 골인까지 최소 몇 칸 (지름길 포함). 못 가면 MAX_DISTANCE
    static int remaining(BoardGraph graph, BoardNode node, BoardNode cameFrom) {
        for (int steps = 1; steps < MAX_DISTANCE; steps++) {
            for (BoardPath path : graph.getMovePaths(node, cameFrom, steps)) {
                if (path.finished()) return steps;
            }
        }
        return MAX_DISTANCE;
    }
}
