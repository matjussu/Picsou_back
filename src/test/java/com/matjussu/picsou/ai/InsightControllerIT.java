package com.matjussu.picsou.ai;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.matjussu.picsou.auth.dto.AuthResponse;
import com.matjussu.picsou.auth.dto.SignupRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Insights : le client IA est stubé (@MockitoBean) → jamais d'appel à l'API réelle. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class InsightControllerIT {

  @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", pg::getJdbcUrl);
    r.add("spring.datasource.username", pg::getUsername);
    r.add("spring.datasource.password", pg::getPassword);
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @MockitoBean AiClient aiClient;

  private String signup(String email) throws Exception {
    var req = new SignupRequest(email, "Strong-Password-123", "AI");
    var res =
        mvc.perform(
                post("/api/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andReturn();
    return json.readValue(res.getResponse().getContentAsString(), AuthResponse.class).accessToken();
  }

  @Test
  void generates_then_serves_from_cache() throws Exception {
    when(aiClient.writeMonthlyInsight(any()))
        .thenReturn(new InsightResult("Ton mois est équilibré.", 123, "claude-sonnet-4-6"));
    String token = signup("ai-cache@picsou.demo");

    mvc.perform(post("/api/insights/monthly").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.text").value("Ton mois est équilibré."))
        .andExpect(jsonPath("$.cached").value(false))
        .andExpect(jsonPath("$.model").value("claude-sonnet-4-6"))
        .andExpect(jsonPath("$.tokensUsed").value(123))
        .andExpect(jsonPath("$.facts").exists());

    // 2e appel < 24h → servi depuis le cache, le LLM n'est PAS rappelé.
    mvc.perform(post("/api/insights/monthly").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cached").value(true));

    verify(aiClient, times(1)).writeMonthlyInsight(any());
  }

  @Test
  void returns_503_when_ai_unavailable() throws Exception {
    when(aiClient.writeMonthlyInsight(any()))
        .thenThrow(new AiUnavailableException("IA non configurée"));
    String token = signup("ai-unavailable@picsou.demo");

    mvc.perform(post("/api/insights/monthly").header("Authorization", "Bearer " + token))
        .andExpect(status().isServiceUnavailable());
  }
}
