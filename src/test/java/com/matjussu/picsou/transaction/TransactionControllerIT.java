package com.matjussu.picsou.transaction;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.matjussu.picsou.auth.dto.AuthResponse;
import com.matjussu.picsou.auth.dto.SignupRequest;
import com.matjussu.picsou.transaction.dto.CreateTransactionRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
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
class TransactionControllerIT {

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
    var signup = new SignupRequest(email, "Strong-Password-123", "Tx");
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

  private UUID createTx(String token, String amount, String date, String desc, TransactionType type)
      throws Exception {
    var req =
        new CreateTransactionRequest(
            new BigDecimal(amount), LocalDate.parse(date), desc, type, null, null, null);
    var body =
        mvc.perform(
                post("/api/transactions")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(json.readTree(body).get("id").asText());
  }

  @Test
  void create_then_get_transaction_uses_default_account() throws Exception {
    String token = signupAndGetToken("tx-create@picsou.demo");
    UUID id = createTx(token, "42.50", "2026-05-10", "Resto", TransactionType.expense);

    mvc.perform(get("/api/transactions/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("Resto"))
        .andExpect(jsonPath("$.source").value("manual"))
        .andExpect(jsonPath("$.accountId").isNotEmpty()); // fallback compte par défaut
  }

  @Test
  void filter_by_type_and_text_via_http() throws Exception {
    String token = signupAndGetToken("tx-filter@picsou.demo");
    createTx(token, "1000", "2026-05-01", "Salaire", TransactionType.income);
    createTx(token, "20", "2026-05-10", "Resto", TransactionType.expense);
    createTx(token, "50", "2026-05-20", "Courses", TransactionType.expense);

    mvc.perform(
            get("/api/transactions")
                .param("type", "expense")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));

    mvc.perform(
            get("/api/transactions").param("q", "resto").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].description").value("Resto"));
  }

  @Test
  void filter_by_date_range_via_http() throws Exception {
    String token = signupAndGetToken("tx-date@picsou.demo");
    createTx(token, "10", "2026-04-01", "Avril", TransactionType.expense);
    createTx(token, "10", "2026-05-15", "Mai", TransactionType.expense);
    createTx(token, "10", "2026-06-01", "Juin", TransactionType.expense);

    mvc.perform(
            get("/api/transactions")
                .param("from", "2026-05-01")
                .param("to", "2026-05-31")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].description").value("Mai"));
  }

  @Test
  void cannot_see_other_users_transactions() throws Exception {
    String tokenB = signupAndGetToken("tx-userb@picsou.demo");
    createTx(tokenB, "500", "2026-05-10", "Secret B", TransactionType.income);

    String tokenA = signupAndGetToken("tx-usera@picsou.demo");
    mvc.perform(get("/api/transactions").header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void delete_transaction_then_404() throws Exception {
    String token = signupAndGetToken("tx-delete@picsou.demo");
    UUID id = createTx(token, "15", "2026-05-10", "À supprimer", TransactionType.expense);

    mvc.perform(delete("/api/transactions/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/transactions/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
  }
}
