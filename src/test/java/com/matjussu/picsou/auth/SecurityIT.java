package com.matjussu.picsou.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.matjussu.picsou.auth.jwt.JwtTokenProvider;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that the JWT auth filter only accepts access tokens on protected endpoints. A refresh
 * token must never authenticate a request (token type confusion). A test-only protected controller
 * is registered because the Phase 1 scaffolding exposes no protected endpoint yet.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class SecurityIT {

  @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", pg::getJdbcUrl);
    r.add("spring.datasource.username", pg::getUsername);
    r.add("spring.datasource.password", pg::getPassword);
  }

  @Autowired MockMvc mvc;
  @Autowired JwtTokenProvider tokenProvider;

  @Test
  void refresh_token_is_rejected_on_protected_endpoint() throws Exception {
    String refresh = tokenProvider.generateRefreshToken(UUID.randomUUID());
    mvc.perform(get("/api/test/secure").header("Authorization", "Bearer " + refresh))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void access_token_is_accepted_on_protected_endpoint() throws Exception {
    String access = tokenProvider.generateAccessToken(UUID.randomUUID());
    mvc.perform(get("/api/test/secure").header("Authorization", "Bearer " + access))
        .andExpect(status().isOk());
  }

  @Test
  void actuator_health_is_public() throws Exception {
    // Render (et tout monitoring externe) doit pouvoir ping le health check sans JWT.
    mvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }
}
