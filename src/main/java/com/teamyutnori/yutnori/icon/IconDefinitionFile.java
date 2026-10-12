package com.teamyutnori.yutnori.icon;

import java.util.List;

// profile-icons.json 파일 전체 구조 (Unity ProfileIconDefinitionFile과 같은 모양 + weight)
// {
//   "version": 1,
//   "icons": [ { "id": ..., "name": ..., "weight": ... }, ... ]
// }
public record IconDefinitionFile(int version, List<IconDefinition> icons) {}
