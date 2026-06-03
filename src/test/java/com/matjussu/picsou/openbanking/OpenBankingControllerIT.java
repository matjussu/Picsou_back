package com.matjussu.picsou.openbanking;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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

  private JsonNode connect(String token, String institutionId) throws Exception {
    var body = "{\"institutionId\":\"" + institutionId + "\"}";
    var result =
        mvc.perform(
                post("/api/openbanking/connections")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();
    return json.readTree(result.getResponse().getContentAsString());
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

  @Test
  void connect_then_list_sync_disconnect_flow() throws Exception {
    String token = signupAndGetToken("ob-flow@picsou.demo");

    // Connexion : crée la connexion + compte + importe des transactions.
    JsonNode conn = connect(token, "revolut");
    String connId = conn.get("id").asText();
    org.junit.jupiter.api.Assertions.assertEquals("revolut", conn.get("institutionSlug").asText());
    org.junit.jupiter.api.Assertions.assertEquals("active", conn.get("status").asText());
    org.junit.jupiter.api.Assertions.assertFalse(conn.get("accountId").isNull());
    org.junit.jupiter.api.Assertions.assertTrue(conn.get("transactionsImported").asLong() > 0);

    // Liste : la connexion apparaît.
    mvc.perform(get("/api/openbanking/connections").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].institutionSlug").value("revolut"));

    // Re-synchro : idempotent → 0 nouvelle transaction.
    mvc.perform(
            post("/api/openbanking/connections/" + connId + "/sync")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.connectionId").value(connId))
        .andExpect(jsonPath("$.transactionsImported").value(0));

    // Les transactions importées remontent dans la liste, taguées source=openbanking.
    String accountId = conn.get("accountId").asText();
    mvc.perform(
            get("/api/transactions?accountId=" + accountId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].source").value("openbanking"));

    // Déconnexion → 204, puis la connexion passe en révoquée.
    mvc.perform(
            delete("/api/openbanking/connections/" + connId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/openbanking/connections").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].status").value("revoked"));

    // Re-synchro d'une connexion révoquée → 409.
    mvc.perform(
            post("/api/openbanking/connections/" + connId + "/sync")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isConflict());
  }

  @Test
  void connect_unknown_bank_returns_404() throws Exception {
    String token = signupAndGetToken("ob-unknown@picsou.demo");
    mvc.perform(
            post("/api/openbanking/connections")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"institutionId\":\"banque-de-narnia\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void cross_user_cannot_sync_or_disconnect_others_connection() throws Exception {
    String tokenA = signupAndGetToken("ob-a@picsou.demo");
    String tokenB = signupAndGetToken("ob-b@picsou.demo");
    String connId = connect(tokenA, "lcl").get("id").asText();

    // B ne peut ni synchroniser ni déconnecter la connexion de A → 404 (frontière user-scoping).
    mvc.perform(
            post("/api/openbanking/connections/" + connId + "/sync")
                .header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isNotFound());
    mvc.perform(
            delete("/api/openbanking/connections/" + connId)
                .header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isNotFound());

    // A garde sa connexion intacte et active (B n'a rien pu révoquer).
    mvc.perform(get("/api/openbanking/connections").header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].status").value("active"));
  }

  @Test
  void second_sync_is_idempotent_no_new_transactions() throws Exception {
    String token = signupAndGetToken("ob-idem@picsou.demo");
    JsonNode conn = connect(token, "lcl");
    String connId = conn.get("id").asText();
    long importedAtConnect = conn.get("transactionsImported").asLong();
    org.junit.jupiter.api.Assertions.assertTrue(importedAtConnect > 0);

    // 2e synchro → aucune nouvelle transaction (idempotent par external_id).
    mvc.perform(
            post("/api/openbanking/connections/" + connId + "/sync")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.transactionsImported").value(0));

    // Le compteur de transactions de la connexion est inchangé.
    mvc.perform(get("/api/openbanking/connections").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].transactionsImported").value((int) importedAtConnect));
  }

  @Test
  void connect_same_bank_twice_returns_409() throws Exception {
    String token = signupAndGetToken("ob-dup@picsou.demo");
    connect(token, "n26");
    mvc.perform(
            post("/api/openbanking/connections")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"institutionId\":\"n26\"}"))
        .andExpect(status().isConflict());
  }
}
