package ru.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.auth.model.RefreshToken;
import ru.auth.model.User;
import ru.auth.repository.RefreshTokenRepository;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${auth.refresh.expires:P7D}")
    private Duration refreshTokenTtl;

    private static final SecureRandom random = new SecureRandom();

    public String generateTokenString() {
        byte[] bytes = new byte[64];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public RefreshToken issue(User user) {
        RefreshToken token = RefreshToken.builder()
                .user(user)
                .token(generateTokenString())
                .expiresAt(Instant.now().plus(refreshTokenTtl))
                .revoked(false)
                .build();
        return refreshTokenRepository.save(token);
    }

    public RefreshToken validateActive(String tokenValue) {
        RefreshToken token = refreshTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new IllegalArgumentException("refresh token not found"));
        if (token.isRevoked()) {
            throw new IllegalStateException("refresh token revoked");
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalStateException("refresh token expired");
        }
        return token;
    }

    public void revoke(RefreshToken token) {
        token.setRevoked(true);
        refreshTokenRepository.save(token);
    }

}