package com.teamyutnori.yutnori.match.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 한 판의 참가자 한 줄 (RankingEntry 하나)
// playerId로 전적을 조회하므로 인덱스를 건다
@Entity
@Table(name = "match_participant",
        indexes = @Index(name = "idx_match_participant_player", columnList = "playerId"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id")
    private MatchResult match;

    @Column(nullable = false, length = 36)
    private String playerId;

    @Column(nullable = false)
    private int team;

    // rank는 MySQL 8 예약어라 컬럼 이름을 바꿈
    @Column(name = "player_rank", nullable = false)
    private int ranking;

    @Column(nullable = false)
    private int rewardGold;

    // 기권·끊김으로 나간 플레이어
    @Column(nullable = false)
    private boolean leftGame;

    public static MatchParticipant create(String playerId, int team, int ranking, int rewardGold, boolean leftGame) {
        MatchParticipant p = new MatchParticipant();
        p.playerId = playerId;
        p.team = team;
        p.ranking = ranking;
        p.rewardGold = rewardGold;
        p.leftGame = leftGame;
        return p;
    }

    void assignMatch(MatchResult match) {
        this.match = match;
    }

    public boolean isWin() {
        return ranking == 1;
    }
}