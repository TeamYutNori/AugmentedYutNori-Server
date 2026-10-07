package com.teamyutnori.yutnori.player.controller;

import com.teamyutnori.yutnori.player.dto.CreatePlayerRequest;
import com.teamyutnori.yutnori.player.dto.CreatedPlayerResponse;
import com.teamyutnori.yutnori.player.service.PlayerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/players")
public class PlayerController {

    private final PlayerService playerService;

    // 게스트 플레이어 생성
    @PostMapping
    public ResponseEntity<CreatedPlayerResponse> createPlayer(
            @Valid @RequestBody CreatePlayerRequest request
    ) {
        CreatedPlayerResponse response = playerService.createPlayer(request);

        return ResponseEntity.ok(response);
    }
}