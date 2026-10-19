package com.teamyutnori.yutnori.game.model;

import com.teamyutnori.yutnori.game.augment.AugmentIds;
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
    @Setter private boolean rethrownThisTurn = false; // 이번 턴에 야바위꾼(다시 던지기)을 썼는지 (턴당 1번)
    // 턴 번호. 새 턴이 시작될 때마다 1씩 증가 (TurnManager가 관리, 첫 턴 = 1)
    // 턴 타이머가 "자기가 시작된 턴"에만 동작하게 하는 표식으로 쓴다
    @Setter private int turnNumber = 0;
    // 라운드 번호. 모든 플레이어가 한 번씩 차례를 돌면 1 증가 (TurnManager가 관리, 첫 턴 = 1, 증강 선택 중 = 0)
    // 남은 사람이 줄면 한 바퀴도 짧아진다. 보상 인원 계산(기준 라운드 전 이탈)에 쓴다
    @Setter private int roundNumber = 0;

    // ── 윷 결과 ──
    private final List<YutResult> storedResults = new ArrayList<>(); // 던졌지만 아직 이동에 안 쓴 결과
    @Setter private YutResult latestResult;     // 마지막으로 던진 결과 (재던지기 판단용)

    // ── 증강 ──
    private final Map<Integer, List<String>> offeredAugments = new HashMap<>(); // 팀 → 제시된 후보
    private final Map<Integer, Set<String>> ownedAugments = new HashMap<>();    // 팀 → 보유 증강

    // ── 결과 ──
    @Setter private Integer winnerTeam;         // 끝나기 전엔 null

    // ── 퇴장 ──
    private final Set<Integer> removedTeams = new LinkedHashSet<>();   // 나간 팀 (나간 순서대로)
    private final Map<Integer, Integer> removedAtRound = new HashMap<>(); // 나간 팀 → 나간 순간의 roundNumber (보상 인원 계산용)

    // ── 자동 진행 ──
    private final Set<Integer> autoTeams = new HashSet<>();   // 연결이 끊겨 서버가 대신 두는 팀

    // board: BoardLayoutRepository.create()로 이 게임 전용으로 만든 판 (GameService.startGame에서 넘겨줌)
    public GameSession(String roomCode, GameSetup setup, Map<String, Integer> playerTeams, BoardGraph board) {
        this.roomCode = roomCode;
        this.setup = setup;
        this.playerTeams = Map.copyOf(playerTeams);
        this.board = board;
        this.boardState = new BoardState(board, List.of());   // 말은 증강 선택이 끝난 뒤 createPieces()로 만든다 (팀별 말 수가 증강에 따라 달라서)
        for (int t = 0; t < setup.teamCount(); t++) {
            ownedAugments.put(t, new HashSet<>());
            offeredAugments.put(t, List.of());        // 아직 제시 안 함 = 빈 목록
        }
    }

    // ── 참가자 ──
    public Optional<Integer> teamOf(String playerId) { return Optional.ofNullable(playerTeams.get(playerId)); }

    // 팀의 플레이어 id (플레이어 = 팀)
    public Optional<String> playerOf(int team) {
        return playerTeams.entrySet().stream()
                .filter(e -> e.getValue() == team)
                .map(Map.Entry::getKey)
                .findFirst();
    }

    // ── 말 생성 ──
    // 모든 팀의 증강이 정해지면 한 번 만든다 (Unity: 마지막 AUGMENT_SELECTED 때 생성, 같은 규칙)
    //  - 나간 팀(증강을 고른 뒤 나간 경우 포함)은 말을 만들지 않는다
    //  - 팀별 말 수 = piecesFor(team), 슬롯 0부터 (노장 팀은 마지막 슬롯 없음: 4개 기준 100·101·102)
    // 이미 있는 말은 그대로 둔다. 체이닝용으로 자기 자신을 돌려준다
    public GameSession createPieces() {
        for (int team = 0; team < setup.teamCount(); team++) {
            if (removedTeams.contains(team)) continue;
            for (int slot = 0; slot < piecesFor(team); slot++) {
                boardState.addPiece(GameSetup.pieceId(team, slot));
            }
        }
        return this;
    }

    // 팀의 말 수: 기본 말 수에서 노장이면 1개 적게 (Unity VeteranAugment.PieceCountFor와 같은 규칙)
    public int piecesFor(int team) {
        return setup.piecesPerTeam() - (hasAugment(team, AugmentIds.VETERAN) ? 1 : 0);
    }

    // ── 퇴장 ──
    public boolean isRemoved(int team) { return removedTeams.contains(team); }
    // 나간 순간의 라운드 번호도 함께 기록한다 (증강 선택 중이면 0). 이미 나간 팀이면 처음 기록을 유지
    public void markRemoved(int team) {
        if (removedTeams.add(team)) removedAtRound.put(team, roundNumber);
    }
    // 나간 팀이 나간 라운드 번호. 안 나간 팀이면 빈 값
    public OptionalInt removedAtRound(int team) {
        Integer round = removedAtRound.get(team);
        return round == null ? OptionalInt.empty() : OptionalInt.of(round);
    }

    // ── 자동 진행 (끊김 → 켬, 재접속 → 끔) ──
    public boolean isAuto(int team) { return autoTeams.contains(team); }
    public void setAuto(int team, boolean auto) {
        if (auto) autoTeams.add(team);
        else autoTeams.remove(team);
    }

    // 아직 게임에 남아 있는 팀 (팀 번호 순)
    public List<Integer> activeTeams() {
        List<Integer> teams = new ArrayList<>();
        for (int t = 0; t < setup.teamCount(); t++) {
            if (!removedTeams.contains(t)) teams.add(t);
        }
        return teams;
    }

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
