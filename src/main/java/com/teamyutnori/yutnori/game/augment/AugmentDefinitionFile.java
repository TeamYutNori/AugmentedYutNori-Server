package com.teamyutnori.yutnori.game.augment;

import java.util.List;

// augments.json 파일 전체 구조 (Unity AugmentDefinitionFile과 동일)
// {
//   "version": 1,
//   "augments": [ { "id": ..., "name": ..., "description": ..., "consumable": ... }, ... ]
// }
public record AugmentDefinitionFile(int version, List<AugmentDefinition> augments) {}
