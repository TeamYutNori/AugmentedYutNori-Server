package com.teamyutnori.yutnori.icon.controller;

import com.teamyutnori.yutnori.auth.TokenProvider;
import com.teamyutnori.yutnori.icon.dto.OwnedIconsResponse;
import com.teamyutnori.yutnori.icon.service.IconService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class IconController {

    private final IconService service;
    private final TokenProvider tokenProvider;

    @GetMapping("/players/me/icons")
    public ResponseEntity<OwnedIconsResponse> ownedIcons(
            @RequestHeader("Authorization") String authorization
    ) {
        OwnedIconsResponse response = service.ownedIcons(playerId(authorization));
        return ResponseEntity.ok(response);
    }

    private String playerId(String authorization) {
        String token = authorization.replaceFirst("^Bearer ", "");
        return tokenProvider.getPlayerId(token);
    }
}
