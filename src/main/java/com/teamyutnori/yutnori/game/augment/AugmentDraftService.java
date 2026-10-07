package com.teamyutnori.yutnori.game.augment;

import com.teamyutnori.yutnori.game.model.GameSession;
import com.teamyutnori.yutnori.game.yut.RandomProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// 팀별 증강 후보 뽑기 (Unity AugmentPool.GetRandomIds의 서버판)
//
// 서버가 후보를 정하는 이유: 클라이언트가 뽑으면 마음에 드는 후보가 나올 때까지 다시 뽑는 조작이 가능하다.
// 그래서 온라인에서는 서버가 뽑아서 AUGMENT_CHOICES로 보내고, 클라는 받은 후보만 보여 준다 (Unity AugmentPool.SetOffered).
@Component
@RequiredArgsConstructor
public class AugmentDraftService {

    private final AugmentCatalog catalog;

    // 실행 시에는 SecureRandomProvider가 주입된다.
    // 테스트에서는 SequenceRandomProvider를 넣으면 어떤 후보가 나올지 정해 둘 수 있다.
    private final RandomProvider random;

    // team이 아직 갖고 있지 않은 증강 중에서 count개를 중복 없이 골라 돌려준다.
    // 후보가 count보다 적으면 남은 것만큼만 돌려준다 (예: 남은 증강이 2개면 2개)
    public List<String> draft(GameSession session, int team, int count) {
        // 1) 후보 모으기: 전체 목록(json 순서)에서 이미 가진 증강은 뺀다
        List<String> candidates = new ArrayList<>();
        for (String id : catalog.allIds()) {
            if (!session.hasAugment(team, id)) {
                candidates.add(id);
            }
        }

        // 2) 앞에서부터 count개 자리만 무작위로 섞는다 (부분 Fisher–Yates 셔플)
        //    i번째 자리에 i ~ 끝 중 하나를 골라 바꿔 넣는다 → 한 번 뽑힌 것은 다시 안 뽑혀서 중복이 없다
        //    Unity AugmentPool.GetRandomIds와 같은 방식
        int pickCount = Math.max(0, Math.min(count, candidates.size()));
        for (int i = 0; i < pickCount; i++) {
            int j = nextIndex(i, candidates.size());
            Collections.swap(candidates, i, j);
        }

        // 3) 앞의 pickCount개가 후보. 바깥에서 수정하지 못하게 복사본으로 돌려준다
        return List.copyOf(candidates.subList(0, pickCount));
    }

    // min 이상 max 미만의 정수 (nextDouble()은 1.0 미만이라 max는 나오지 않는다)
    private int nextIndex(int min, int max) {
        return min + (int) (random.nextDouble() * (max - min));
    }
}
