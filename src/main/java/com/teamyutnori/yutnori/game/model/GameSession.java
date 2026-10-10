package com.teamyutnori.yutnori.game.model;

import com.teamyutnori.yutnori.game.board.BoardGraph;
import com.teamyutnori.yutnori.game.board.BoardState;
import com.teamyutnori.yutnori.game.yut.YutResult;
import lombok.Getter;
import lombok.Setter;
import java.util.*;

@Getter
public class GameSession {

    private final String roomCode;
    private final GameSetup setup;  // 팀 수, 말 수, 보드 ID
    private final BoardGraph board;       // 판 모양 (칸 번호, 연결, 지름길)
    private final BoardState boardState;  // 모든 말 상태

    // ── 참가자 ──
    private final Map<String, Integer> playerTeams; // playerId → 팀 (2번 RoomService가 시작할 때 넘겨줌)

    // ── 진행 단계 ──
    @Setter private GamePhase phase = GamePhase.AUGMENT_SELECT;

    // ── 턴 ──
    @Setter private int currentTeam = 0;
    @Setter private int remainingThrows = 0;    // 이번 턴에 더 던질 수 있는 횟수
    @Setter private boolean movedThisTurn = false; // 이번 턴에 말을 움직인 적이 있는지 체크
    // 턴 번호. 새 턴이 시작될 때마다 1씩 증가 (TurnManager가 관리, 첫 턴 = 1)
    // 턴 타이머가 "자기가 시작된 턴"에만 동작하게 하는 표식으로 쓴다
    @Setter private int turnNumber = 0;

    // ── 윷 결과 ──
    private final List<YutResult> storedResults = new ArrayList<>(); // 던졌지만 아직 이동에 안 쓴 결과
    @Setter private YutResult latestResult;     // 마지막으로 던진 결과 (재던지기 판단용)

    // ── 증강 ──
    private final Map<Integer, List<String>> offeredAugments = new HashMap<>(); // 팀 → 제시된 후보
    private final Map<Integer, Set<String>> ownedAugments = new HashMap<>();    // 팀 → 보유 증강

    // ── 결과 ──
    @Setter private Integer winnerTeam;         // 끝나기 전엔 null

    // board: BoardLayoutRepository.create()로 이 게임 전용으로 만든 판 (GameService.startGame에서 넘겨줌)
    public GameSession(String roomCode, GameSetup setup, Map<String, Integer> playerTeams, BoardGraph board) {
        this.roomCode = roomCode;
        this.setup = setup;
        this.playerTeams = Map.copyOf(playerTeams);
        this.board = board;
        this.boardState = new BoardState(board, setup.allPieceIds());   // 모든 말은 대기석에서 시작
        for (int t = 0; t < setup.teamCount(); t++) {
            ownedAugments.put(t, new HashSet<>());
            offeredAugments.put(t, List.of());        // 아직 제시 안 함 = 빈 목록
        }
    }

    // ── 참가자 ──
    public Optional<Integer> teamOf(String playerId) { return Optional.ofNullable(playerTeams.get(playerId)); }

    // ── 제시 후보 ──
    public void setOffered(int team, List<String> ids) { offeredAugments.put(team, List.copyOf(ids)); }
    public boolean isOffered(int team, String id)      { return offeredAugments.get(team).contains(id); }
    public boolean hasOffer(int team)                  { return !offeredAugments.get(team).isEmpty(); }
    public boolean hasAnyPendingOffer()                { return offeredAugments.values().stream().anyMatch(l -> !l.isEmpty()); }
    public void clearOffered(int team)                 { offeredAugments.put(team, List.of()); }

    // ── 보유 증강 ──
    public boolean hasAugment(int team, String id) { return ownedAugments.get(team).contains(id); }
    public void addAugment(int team, String id)    { ownedAugments.get(team).add(id); }
    public void removeAugment(int team, String id) { ownedAugments.get(team).remove(id); }
}
