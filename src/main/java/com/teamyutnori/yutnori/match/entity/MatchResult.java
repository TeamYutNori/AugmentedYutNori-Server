package com.teamyutnori.yutnori.match.entity;

import com.teamyutnori.yutnori.game.model.GameEndReason;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// 한 판 기록 (GameFinishedEvent 하나 = MatchResult 하나)
// 방 코드는 여러 판에서 재사용될 수 있어서 unique가 아님
@Entity
@Table(name = "match_result")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String roomCode;

    // 남은 팀 없이 끝나면 -1
    @Column(nullable = false)
    private int winnerTeam;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GameEndReason reason;

    @Column(nullable = false)
    private int playerCount;

    @Column(nullable = false)
    private Instant playedAt;

    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MatchParticipant> participants = new ArrayList<>();

    public static MatchResult create(String roomCode, int winnerTeam, GameEndReason reason, Instant playedAt) {
        MatchResult match = new MatchResult();
        match.roomCode = roomCode;
        match.winnerTeam = winnerTeam;
        match.reason = reason;
        match.playedAt = playedAt;
        return match;
    }

    // 참가자 추가 (양방향 연결 + 인원 수 갱신)
    public void addParticipant(MatchParticipant participant) {
        participants.add(participant);
        participant.assignMatch(this);
        playerCount = participants.size();
    }
}