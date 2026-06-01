package com.matjussu.picsou.auth.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

  private JwtTokenProvider provider;
  // 64 bytes base64 → 96 bytes decoded, valid for HS512 (>=64 bytes required)
  private static final String SECRET =
      "dGVzdC1zZWNyZXQtdmVyeS1sb25nLWtleS1mb3ItaG1hYy1zaGE1MTItYWxnby1taW4tNjQtYnl0ZXMtcmVxdWlyZWQtZm9yLWp3dC1zaWdu";

  @BeforeEach
  void setUp() {
    provider = new JwtTokenProvider(SECRET, 900_000L, 604_800_000L);
  }

  @Test
  void generates_and_validates_access_token() {
    UUID userId = UUID.randomUUID();
    String token = provider.generateAccessToken(userId);
    assertNotNull(token);
    assertEquals(userId, provider.extractUserId(token));
    assertTrue(provider.isValid(token));
  }

  @Test
  void generates_distinct_refresh_token() {
    UUID userId = UUID.randomUUID();
    String access = provider.generateAccessToken(userId);
    String refresh = provider.generateRefreshToken(userId);
    assertNotEquals(access, refresh);
    assertTrue(provider.isValid(refresh));
  }

  @Test
  void rejects_tampered_token() {
    UUID userId = UUID.randomUUID();
    String token = provider.generateAccessToken(userId);
    String tampered = token.substring(0, token.length() - 5) + "XXXXX";
    assertFalse(provider.isValid(tampered));
  }

  @Test
  void rejects_refresh_token_when_validating_as_access_token() {
    UUID userId = UUID.randomUUID();
    String refresh = provider.generateRefreshToken(userId);
    assertFalse(provider.isValidAccessToken(refresh));
  }

  @Test
  void accepts_access_token_when_validating_as_access_token() {
    UUID userId = UUID.randomUUID();
    String access = provider.generateAccessToken(userId);
    assertTrue(provider.isValidAccessToken(access));
  }

  @Test
  void rejects_tampered_token_as_access_token() {
    UUID userId = UUID.randomUUID();
    String access = provider.generateAccessToken(userId);
    String tampered = access.substring(0, access.length() - 5) + "XXXXX";
    assertFalse(provider.isValidAccessToken(tampered));
  }
}
