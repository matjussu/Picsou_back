package com.matjussu.picsou.openbanking;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class OpenBankingControllerIT {

  @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", pg::getJdbcUrl);
    r.add("spring.datasource.username", pg::getUsername);
    r.add("spring.datasource.password", pg::getPassword);
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  private String signupAndGetToken(String email) throws Exception {
    var signup = new SignupRequest(email, "Strong-Password-123", "Ob");
    var result =
        mvc.perform(
                post("/api/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(signup)))
            .andExpect(status().isCreated())
            .andReturn();
    return json.readValue(result.getResponse().getContentAsString(), AuthResponse.class)
        .accessToken();
  }

  @Test
  void institutions_returns_catalog_with_slug_name_and_brand_color() throws Exception {
    String token = signupAndGetToken("ob-it@picsou.demo");

    mvc.perform(get("/api/openbanking/institutions").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(10))
        .andExpect(jsonPath("$[0].slug").value("bnp-paribas"))
        .andExpect(jsonPath("$[0].name").value("BNP Paribas"))
        .andExpect(jsonPath("$[0].brandColor").value("#008753"));
  }

  @Test
  void institutions_without_token_returns_401() throws Exception {
    mvc.perform(get("/api/openbanking/institutions")).andExpect(status().isUnauthorized());
  }
}
