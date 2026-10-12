package com.teamyutnori.yutnori.match.repository;

import com.teamyutnori.yutnori.match.entity.MatchResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchResultRepository extends JpaRepository<MatchResult, Long> {
}