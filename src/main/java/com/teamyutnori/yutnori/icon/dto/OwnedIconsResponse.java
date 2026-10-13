package com.teamyutnori.yutnori.icon.dto;

import java.util.List;

// GET /players/me/icons 응답 — 내가 가진 아이콘 id만 (전체 목록·이름·그림은 클라 profile_icons.json)
public record OwnedIconsResponse(List<String> iconIds) { }
