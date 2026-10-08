package com.teamyutnori.yutnori.auth;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.teamyutnori.yutnori.common.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FirebaseTokenVerifier {
    private final FirebaseAuth firebaseAuth;

    public VerifiedUser verify(String idToken) {
        try {
            FirebaseToken token = firebaseAuth.verifyIdToken(idToken);
            String name = null;
            Object nameClaim = token.getClaims().get("name");

            if (nameClaim != null) {
                name = nameClaim.toString();
            }
            return new VerifiedUser(
                    token.getUid(),
                    token.getEmail(),
                    name
            );
        } catch (FirebaseAuthException e) {
            throw new UnauthorizedException(
                    "INVALID_FIREBASE_TOKEN",
                    "유효하지 않은 Firebase 인증 토큰입니다."
            );
        }
    }

    public record VerifiedUser(
            String uid,
            String email,
            String name
    ) {
    }
}