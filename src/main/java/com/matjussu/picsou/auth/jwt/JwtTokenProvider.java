package com.matjussu.picsou.auth.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

  private final SecretKey key;
  private final long accessExpirationMs;
  private final long refreshExpirationMs;

  public JwtTokenProvider(
      @Value("${app.jwt.secret}") String secret,
      @Value("${app.jwt.access-expiration-ms}") long accessExpirationMs,
      @Value("${app.jwt.refresh-expiration-ms}") long refreshExpirationMs) {
    this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
    this.accessExpirationMs = accessExpirationMs;
    this.refreshExpirationMs = refreshExpirationMs;
  }

  public String generateAccessToken(UUID userId) {
    return buildToken(userId, "access", accessExpirationMs);
  }

  public String generateRefreshToken(UUID userId) {
    return buildToken(userId, "refresh", refreshExpirationMs);
  }

  private String buildToken(UUID userId, String type, long expirationMs) {
    Date now = new Date();
    return Jwts.builder()
        .id(UUID.randomUUID().toString())
        .subject(userId.toString())
        .claim("type", type)
        .issuedAt(now)
        .expiration(new Date(now.getTime() + expirationMs))
        .signWith(key, Jwts.SIG.HS512)
        .compact();
  }

  public UUID extractUserId(String token) {
    String sub =
        Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();
    return UUID.fromString(sub);
  }

  public boolean isValid(String token) {
    try {
      Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
      return true;
    } catch (Exception e) {
      return false;
    }
  }
}
