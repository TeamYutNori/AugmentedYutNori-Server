package com.teamyutnori.yutnori.game.board;

// 말 하나 (Unity Piece). 위치와 방향(직전 칸)만 들고, 상태 변경은 BoardState만 한다
public class Piece {

    private final int id;     // 팀 * 100 + 순번 (GameSetup.pieceId). 모든 기기에서 같은 말을 가리키는 번호
    private final int team;
    private final BoardNode startNode;

    private PieceState state = PieceState.WAITING;
    private BoardNode node;      // 현재 칸 (대기·완주 말은 startNode. 둘은 state로 구분)
    private BoardNode cameFrom;  // 직전 칸 (대기 말은 null)

    Piece(int id, int team, BoardNode startNode) {
        this.id = id;
        this.team = team;
        this.startNode = startNode;
        this.node = startNode;
    }

    public int getId()            { return id; }
    public int getTeam()          { return team; }
    public PieceState getState()  { return state; }
    public BoardNode getNode()    { return node; }
    public BoardNode getCameFrom(){ return cameFrom; }

    public boolean isWaiting()  { return state == PieceState.WAITING; }
    public boolean isOnBoard()  { return state == PieceState.ON_BOARD; }
    public boolean isFinished() { return state == PieceState.FINISHED; }

    void move(BoardPath path) {
        node = path.destination();
        cameFrom = path.cameFrom();

        if (path.finished())        state = PieceState.FINISHED;
        else if (node == startNode) state = PieceState.WAITING;
        else                        state = PieceState.ON_BOARD;
    }

    // 잡혔을 때 대기석으로
    void returnToStart() {
        node = startNode;
        cameFrom = null;
        state = PieceState.WAITING;
    }

    @Override
    public String toString() { return "Piece" + id + "(Team" + team + ", " + state + ", " + node + ")"; }
}
