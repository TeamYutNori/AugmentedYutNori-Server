package com.teamyutnori.yutnori.game.model;

import com.teamyutnori.yutnori.game.yut.YutResult;
import lombok.Getter;
import lombok.Setter;
import java.util.*;

@Getter
public class GameSession {

    private final String roomCode;
    private final GameSetup setup;  // 팀 수, 말 수, 보드 ID

    // ── 진행 단계 ──
    @Setter private GamePhase phase = GamePhase.AUGMENT_SELECT;

    // ── 턴 ──
    @Setter private int currentTeam = 0;
    @Setter private int remainingThrows = 0;    // 이번 턴에 더 던질 수 있는 횟수
    @Setter private boolean movedThisTurn = false; // 이번 턴에 말을 움직인 적이 있는지 체크

    // ── 윷 결과 ──
    private final List<YutResult> storedResults = new ArrayList<>(); // 던졌지만 아직 이동에 안 쓴 결과
    @Setter private YutResult latestResult;     // 마지막으로 던진 결과 (재던지기 판단용)

    // ── 증강 ──
    private final Map<Integer, List<String>> offeredAugments = new HashMap<>(); // 팀 → 제시된 후보
    private final Map<Integer, Set<String>> ownedAugments = new HashMap<>();    // 팀 → 보유 증강

    // ── 결과 ──
    @Setter private Integer winnerTeam;         // 끝나기 전엔 null

    public GameSession(String roomCode, GameSetup setup) {
        this.roomCode = roomCode;
        this.setup = setup;
        for (int t = 0; t < setup.teamCount(); t++) {
            ownedAugments.put(t, new HashSet<>());
            offeredAugments.put(t, List.of());        // 아직 제시 안 함 = 빈 목록
        }
    }

    // ── 제시 후보 ──
    public void setOffered(int team, List<String> ids) { offeredAugments.put(team, List.copyOf(ids)); }
    public boolean isOffered(int team, String id)      { return offeredAugments.get(team).contains(id); }
    public void clearOffered(int team)                 { offeredAugments.put(team, List.of()); }

    // ── 보유 증강 ──
    public boolean hasAugment(int team, String id) { return ownedAugments.get(team).contains(id); }
    public void addAugment(int team, String id)    { ownedAugments.get(team).add(id); }
    public void removeAugment(int team, String id) { ownedAugments.get(team).remove(id); }
}