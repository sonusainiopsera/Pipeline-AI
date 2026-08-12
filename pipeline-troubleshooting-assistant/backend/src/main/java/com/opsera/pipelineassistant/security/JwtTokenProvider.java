package com.opsera.pipelineassistant.security;

import com.opsera.pipelineassistant.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Date;
import java.util.HexFormat;

/**
 * Centralises JWT operations: generating access tokens with user claims,
 * generating opaque refresh tokens, validating signatures and expiration,
 * and extracting claims. Signing uses HMAC-SHA256 with a key loaded from
 * application configuration. All configuration values are validated at startup.
 *
 * Never logs token values — only logs validation failure reasons.
 */
@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secret;

    // Expiration values in application.yml are expressed in seconds
    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpirationSeconds;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpirationSeconds;

    @Value("${mfa.challenge-token-expiration:300}")
    private long mfaChallengeTokenExpirationSeconds;

    private SecretKey signingKey;

    @PostConstruct
    public void init() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "jwt.secret must be at least 32 bytes (256 bits) for HMAC-SHA256; "
                    + "current value is only " + keyBytes.length + " byte(s)");
        }
        signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generates a signed JWT access token containing the user's email as subject,
     * their role as a custom claim, and a 15-minute (configurable) expiration.
     */
    public String generateAccessToken(User user) {
        Date now        = new Date();
        Date expiration = new Date(now.getTime() + accessTokenExpirationSeconds * 1000L);

        return Jwts.builder()
                .subject(user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Generates a cryptographically random 256-bit opaque refresh token encoded
     * as a lowercase hex string. This is NOT a JWT — it is stored server-side as
     * a SHA-256 hash in the refresh_tokens table.
     */
    public String generateRefreshToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * Returns true if the token has a valid signature and has not expired;
     * returns false for any invalid condition without throwing.
     */
    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT validation rejected: token expired");
        } catch (MalformedJwtException e) {
            log.warn("JWT validation rejected: malformed token");
        } catch (SignatureException e) {
            log.warn("JWT validation rejected: invalid signature");
        } catch (UnsupportedJwtException e) {
            log.warn("JWT validation rejected: unsupported JWT type");
        } catch (IllegalArgumentException e) {
            log.warn("JWT validation rejected: empty or null claims string");
        }
        return false;
    }

    /**
     * Generates a short-lived JWT (5 min by default) that authorises only the MFA verification step.
     * Carries a {@code type=mfa-challenge} claim so the challenge and access token types
     * cannot be interchanged.
     */
    public String generateMfaChallengeToken(String email) {
        Date now        = new Date();
        Date expiration = new Date(now.getTime() + mfaChallengeTokenExpirationSeconds * 1000L);

        return Jwts.builder()
                .subject(email)
                .claim("type", "mfa-challenge")
                .issuedAt(now)
                .expiration(expiration)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Returns true only if the token has a valid signature, has not expired, and carries
     * {@code type=mfa-challenge}. Rejects regular access tokens.
     */
    public boolean validateMfaChallengeToken(String token) {
        if (!validateToken(token)) {
            return false;
        }
        String type = parseClaims(token).get("type", String.class);
        return "mfa-challenge".equals(type);
    }

    /** Extracts the email (subject claim) from a validated MFA challenge token. */
    public String extractMfaChallengeEmail(String token) {
        return parseClaims(token).getSubject();
    }

    /** Extracts the email (subject claim) from a validated token. */
    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    /** Extracts the role claim from a validated token. */
    public String extractRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
