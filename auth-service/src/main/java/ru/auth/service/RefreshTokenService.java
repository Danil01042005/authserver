package ru.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import ru.auth.entity.RefreshToken;
import ru.auth.entity.User;
import ru.auth.repository.RefreshTokenRepository;

import java.security.SecureRandom;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
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

    public String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot hash token", e);
        }
    }

    public RefreshToken issue(User user) {
        String tokenValue = generateTokenString();
        RefreshToken token = RefreshToken.builder()
                .user(user)
                .tokenHash(sha256(tokenValue))
                .expiresAt(Instant.now().plus(refreshTokenTtl))
                .revoked(false)
                .build();
        RefreshToken saved = refreshTokenRepository.save(token);
        saved.setToken(tokenValue);
        return saved;
    }

    public RefreshToken validateActive(String tokenValue) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(sha256(tokenValue))
                .orElseThrow(() -> new IllegalArgumentException("refresh token not found"));
        if (token.isRevoked()) {
            throw new IllegalStateException("refresh token revoked");
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalStateException("refresh token expired");
        }
        token.setToken(tokenValue);
        return token;
    }

    public void revoke(RefreshToken token) {
        token.setRevoked(true);
        refreshTokenRepository.save(token);
    }

    public RefreshToken rotateByValue(String tokenValue) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(sha256(tokenValue))
                .orElseThrow(() -> new IllegalArgumentException("refresh token not found"));
        if (token.isRevoked()) {
            refreshTokenRepository.deleteByUser(token.getUser());
            throw new IllegalStateException("refresh token revoked");
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalStateException("refresh token expired");
        }
        revoke(token);
        return issue(token.getUser());
    }

    public long purgeExpired() {
        return refreshTokenRepository.deleteByExpiresAtBefore(Instant.now());
    }

    @Scheduled(cron = "0 0 * * * *")
    public void purgeExpiredJob() {
        long deleted = purgeExpired();
        if (deleted > 0) {
            log.info("Purged {} expired refresh tokens", deleted);
        } else {
            log.debug("No expired refresh tokens to purge");
        }
    }
}