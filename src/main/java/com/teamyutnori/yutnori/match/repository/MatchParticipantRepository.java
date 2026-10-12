package com.teamyutnori.yutnori.match.repository;

import com.teamyutnori.yutnori.match.entity.MatchParticipant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MatchParticipantRepository extends JpaRepository<MatchParticipant, Long> {

    // 총 판 수
    long countByPlayerId(String playerId);

    // 1등(승리) 횟수
    long countByPlayerIdAndRanking(String playerId, int ranking);

    // 최근 전적 (판 정보까지 한 번에 가져옴)
    @Query("""
            select p from MatchParticipant p
            join fetch p.match m
            where p.playerId = :playerId
            order by m.playedAt desc
            """)
    List<MatchParticipant> findRecent(@Param("playerId") String playerId, Pageable pageable);
}