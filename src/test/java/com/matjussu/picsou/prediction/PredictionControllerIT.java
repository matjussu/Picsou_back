package com.matjussu.picsou.prediction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.matjussu.picsou.auth.dto.AuthResponse;
import com.matjussu.picsou.auth.dto.SignupRequest;
import com.matjussu.picsou.transaction.Transaction;
import com.matjussu.picsou.transaction.TransactionRepository;
import com.matjussu.picsou.transaction.TransactionType;
import com.matjussu.picsou.user.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
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
class PredictionControllerIT {

  @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", pg::getJdbcUrl);
    r.add("spring.datasource.username", pg::getUsername);
    r.add("spring.datasource.password", pg::getPassword);
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired UserRepository users;
  @Autowired TransactionRepository transactions;
  @Autowired PredictionRepository predictions;

  private String signup(String email) throws Exception {
    var req = new SignupRequest(email, "Strong-Password-123", "Pred");
    var res =
        mvc.perform(
                post("/api/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andReturn();
    return json.readValue(res.getResponse().getContentAsString(), AuthResponse.class).accessToken();
  }

  private void tx(UUID userId, String amount, LocalDate date, TransactionType type) {
    transactions.save(
        Transaction.builder()
            .userId(userId)
            .amount(new BigDecimal(amount))
            .date(date)
            .description("seed")
            .type(type)
            .build());
  }

  @Test
  void end_of_month_with_history_returns_prediction() throws Exception {
    String token = signup("pred-history@picsou.demo");
    UUID userId = users.findByEmail("pred-history@picsou.demo").orElseThrow().getId();
    YearMonth current = YearMonth.now();

    // Mois courant : un revenu + une dépense (le 1er, toujours <= aujourd'hui).
    tx(userId, "1000", current.atDay(1), TransactionType.income);
    tx(userId, "400", current.atDay(1), TransactionType.expense);
    // 3 mois d'historique (net +500 chacun).
    for (int i = 1; i <= 3; i++) {
      YearMonth m = current.minusMonths(i);
      tx(userId, "1000", m.atDay(5), TransactionType.income);
      tx(userId, "500", m.atDay(6), TransactionType.expense);
    }

    mvc.perform(get("/api/predictions/end-of-month").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.predictedBalance").exists())
        .andExpect(jsonPath("$.lowConfidence").value(false))
        .andExpect(jsonPath("$.forecastDate").value(current.atEndOfMonth().toString()));

    // Upsert : un 2e appel ne crée pas de doublon — findByUserIdAndForecastDate (Optional) lèverait
    // NonUniqueResult s'il y avait 2 lignes pour le même (user, forecast_date).
    mvc.perform(get("/api/predictions/end-of-month").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    assertThat(predictions.findByUserIdAndForecastDate(userId, current.atEndOfMonth())).isPresent();
  }

  @Test
  void end_of_month_without_history_is_low_confidence() throws Exception {
    String token = signup("pred-new@picsou.demo");
    UUID userId = users.findByEmail("pred-new@picsou.demo").orElseThrow().getId();
    tx(userId, "200", YearMonth.now().atDay(1), TransactionType.expense);

    mvc.perform(get("/api/predictions/end-of-month").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lowConfidence").value(true));
  }
}
