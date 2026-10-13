package com.teamyutnori.yutnori.icon.service;

import com.teamyutnori.yutnori.icon.PlayerIcon;
import com.teamyutnori.yutnori.icon.dto.OwnedIconsResponse;
import com.teamyutnori.yutnori.icon.PlayerIconRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IconService {

    private final PlayerIconRepository repository;

    @Transactional(readOnly = true)
    public OwnedIconsResponse ownedIcons(String playerId) {
        List<PlayerIcon> owned = repository.findAllByPlayerId(playerId);
        List<String> iconIds = new ArrayList<>();
        for (PlayerIcon icon : owned) {
            iconIds.add(icon.getIconId());
        }
        return new OwnedIconsResponse(iconIds);
    }
}
