package com.teamyutnori.yutnori.game.board;

import com.teamyutnori.yutnori.game.model.GameSetup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// 판 위 모든 말의 상태와 규칙: 업기 / 잡기 / 증강 (Unity BoardState 이식)
// 게임 한 판의 말 상태는 여기서만 바뀐다. GameSession이 하나씩 들고 있다
// 호출하는 쪽(GameService)이 세션 단위로 synchronized를 걸고 쓰므로 여기서는 동기화하지 않는다
public class BoardState {

    private final BoardGraph graph;
    private final Map<Integer, Piece> pieces = new LinkedHashMap<>();   // 넣은 순서 유지 (팀 → 슬롯 순)
    private final List<BoardAugment> augments = new ArrayList<>();

    // pieceIds: GameSetup.allPieceIds() (팀 → 슬롯 순). 모든 말은 대기석에서 시작
    public BoardState(BoardGraph graph, List<Integer> pieceIds) {
        this.graph = graph;
        for (int id : pieceIds) {
            pieces.put(id, new Piece(id, GameSetup.teamOf(id), graph.getStartNode()));
        }
    }

    // ---------- 말 ----------

    public BoardGraph getGraph()            { return graph; }
    public Collection<Piece> getPieces()    { return Collections.unmodifiableCollection(pieces.values()); }
    public Piece getPiece(int id)           { return pieces.get(id); }   // 없으면 null

    public List<Piece> getPiecesOf(int team) {
        List<Piece> result = new ArrayList<>();
        for (Piece piece : pieces.values()) if (piece.getTeam() == team) result.add(piece);
        return result;
    }

    // 판 위 해당 칸에 있는 말들 (대기/골인 말 제외)
    public List<Piece> getPiecesAt(BoardNode node) {
        List<Piece> result = new ArrayList<>();
        for (Piece piece : pieces.values()) {
            if (piece.isOnBoard() && piece.getNode() == node) result.add(piece);
        }
        return result;
    }

    // 함께 움직이는 말 묶음 = 같은 칸의 같은 팀 말 (업힌 말). 판에 안 나온 말은 자기 혼자
    public List<Piece> getStack(Piece piece) {
        if (!piece.isOnBoard()) return List.of(piece);

        List<Piece> stack = getPiecesAt(piece.getNode());
        stack.removeIf(other -> other.getTeam() != piece.getTeam());
        return stack;
    }

    // 팀의 말이 전부 완주했는지 (Unity GameRules.TeamFinished). 말이 하나도 없으면 false
    public boolean teamFinished(int team) {
        boolean hasPiece = false;
        for (Piece piece : pieces.values()) {
            if (piece.getTeam() != team) continue;
            hasPiece = true;
            if (!piece.isFinished()) return false;
        }
        return hasPiece;
    }

    // ---------- 증강 ----------

    // priority 순으로 끼워 넣는다 (같으면 등록 순서). 클라와 호출 순서가 같아야 결과가 같다
    public void addAugment(BoardAugment augment) {
        if (augment == null || augments.contains(augment)) return;

        int index = augments.size();
        for (int i = 0; i < augments.size(); i++) {
            if (augments.get(i).priority() > augment.priority()) { index = i; break; }
        }
        augments.add(index, augment);
    }

    public boolean removeAugment(BoardAugment augment) { return augments.remove(augment); }

    public List<BoardAugment> getAugments(int team) {
        List<BoardAugment> result = new ArrayList<>();
        for (BoardAugment augment : augments) if (augment.ownerTeam() == team) result.add(augment);
        return result;
    }

    // 호출 도중 증강이 추가/제거돼도 안전하도록 복사본으로 돈다 (1회용 증강이 자기 자신을 지우는 경우)
    private List<BoardAugment> snapshotAugments() { return List.copyOf(augments); }

    // ---------- 이동 ----------

    // 말이 moveCount만큼 갈 수 있는 경로들 (증강 보정 적용). 완주한 말이면 빈 목록
    public List<BoardPath> getMovePaths(Piece piece, int moveCount) {
        if (piece == null || piece.isFinished()) return new ArrayList<>();
        return getMovePaths(createContext(piece, moveCount));
    }

    // 증강 보정 후 실제로 움직일 칸 수 (예: 도 + 잔걸음 강화 → 2)
    public int getModifiedMoveCount(Piece piece, int rolledMoveCount) {
        if (piece == null || piece.isFinished()) return rolledMoveCount;
        return createContext(piece, rolledMoveCount).getMoveCount();
    }

    // 이 팀이 moveCount 중 하나로 움직일 수 있는 말이 있는지 (Unity GameRules.HasAnyStoredMove)
    // → TurnManager.afterMove의 hasRemainingAction 값으로 쓴다
    public boolean hasAnyMove(int team, List<Integer> moveCounts) {
        for (Piece piece : pieces.values()) {
            if (piece.getTeam() != team || piece.isFinished()) continue;
            for (int moveCount : moveCounts) {
                if (!getMovePaths(piece, moveCount).isEmpty()) return true;
            }
        }
        return false;
    }

    // 클라가 보낸 이동(말 번호, 사용한 윷 칸 수, 도착 칸)이 규칙상 가능할 때만 적용한다
    // 불가능하면 Optional.empty() → GameService가 INVALID_MOVE로 거부
    // 팀·차례·윷 결과 보유 여부 검사는 GameService/TurnManager가 먼저 한다 (여기선 판 규칙만)
    public Optional<MoveResult> tryMove(int pieceId, int moveCount, int destinationNodeId) {
        Piece piece = getPiece(pieceId);
        BoardNode destination = graph.getNode(destinationNodeId);
        if (piece == null || piece.isFinished() || destination == null) return Optional.empty();

        AugmentContext context = createContext(piece, moveCount);
        for (BoardPath path : getMovePaths(context)) {
            if (path.destination() == destination) return Optional.of(apply(context, path));
        }
        return Optional.empty();
    }

    private List<BoardPath> getMovePaths(AugmentContext context) {
        Piece piece = context.getPiece();
        List<BoardPath> paths = graph.getMovePaths(piece.getNode(), piece.getCameFrom(), context.getMoveCount());
        for (BoardAugment augment : snapshotAugments()) augment.modifyPaths(context, paths);
        return paths;
    }

    // 이번 이동의 컨텍스트. modifyMoveCount 단계까지 적용해 moveCount를 확정한다
    private AugmentContext createContext(Piece piece, int rolledMoveCount) {
        AugmentContext context = new AugmentContext(this, piece, getStack(piece), rolledMoveCount);

        int count = rolledMoveCount;
        for (BoardAugment augment : snapshotAugments()) count = augment.modifyMoveCount(context, count);
        context.setMoveCount(count);
        return context;
    }

    private MoveResult apply(AugmentContext context, BoardPath path) {
        List<Piece> moved = new ArrayList<>(context.getStack());
        for (Piece member : moved) member.move(path);

        // 도착 칸의 상대 팀 말이 기본 잡기 대상 (골인은 판 밖이라 제외). 증강이 목록을 고친 뒤 대기석으로
        List<Piece> captured = new ArrayList<>();
        if (!path.finished()) {
            for (Piece other : getPiecesAt(path.destination())) {
                if (other.getTeam() != context.getTeam()) captured.add(other);
            }
            for (BoardAugment augment : snapshotAugments()) augment.modifyCaptures(context, captured);

            Set<Piece> unique = new HashSet<>();
            captured.removeIf(other -> other == null || !other.isOnBoard() || moved.contains(other) || !unique.add(other));
            for (Piece other : captured) other.returnToStart();
        }

        MoveResult result = new MoveResult(context.getPiece(), context.getRolledMoveCount(), path, moved, captured);
        for (BoardAugment augment : snapshotAugments()) augment.onMoved(context, result);
        return result;
    }
}
