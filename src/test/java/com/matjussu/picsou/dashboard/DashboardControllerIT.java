package com.matjussu.picsou.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.matjussu.picsou.auth.dto.AuthResponse;
import com.matjussu.picsou.auth.dto.SignupRequest;
import com.matjussu.picsou.category.Category;
import com.matjussu.picsou.category.CategoryRepository;
import com.matjussu.picsou.dashboard.dto.CategorySlice;
import com.matjussu.picsou.dashboard.dto.DashboardSummary;
import com.matjussu.picsou.dashboard.dto.MonthlyPoint;
import com.matjussu.picsou.transaction.Transaction;
import com.matjussu.picsou.transaction.TransactionRepository;
import com.matjussu.picsou.transaction.TransactionType;
import com.matjussu.picsou.user.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
class DashboardControllerIT {

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
  @Autowired TransactionRepository txns;
  @Autowired CategoryRepository cats;

  private record Ctx(String token, UUID userId) {}

  private Ctx signup(String email) throws Exception {
    var s = new SignupRequest(email, "Strong-Password-123", "Dash");
    var res =
        mvc.perform(
                post("/api/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(s)))
            .andExpect(status().isCreated())
            .andReturn();
    String token =
        json.readValue(res.getResponse().getContentAsString(), AuthResponse.class).accessToken();
    return new Ctx(token, users.findByEmail(email).orElseThrow().getId());
  }

  private void tx(UUID userId, UUID categoryId, String amount, LocalDate date, TransactionType t) {
    txns.save(
        Transaction.builder()
            .userId(userId)
            .categoryId(categoryId)
            .amount(new BigDecimal(amount))
            .date(date)
            .description("seed")
            .type(t)
            .build());
  }

  private <T> T getJson(String url, String token, Class<T> type) throws Exception {
    var body =
        mvc.perform(get(url).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readValue(body, type);
  }

  @Test
  void summary_reflects_current_month_and_is_user_scoped() throws Exception {
    Ctx a = signup("dash-a@picsou.demo");
    LocalDate today = LocalDate.now();
    tx(a.userId(), null, "1000", today, TransactionType.income);
    tx(a.userId(), null, "300", today, TransactionType.expense);
    tx(a.userId(), null, "50", today, TransactionType.expense);

    // Autre user : ne doit jamais compter dans le résumé de A.
    Ctx b = signup("dash-b@picsou.demo");
    tx(b.userId(), null, "9999", today, TransactionType.expense);

    DashboardSummary s = getJson("/api/dashboard/summary", a.token(), DashboardSummary.class);
    assertThat(s.income()).isEqualByComparingTo("1000");
    assertThat(s.expense()).isEqualByComparingTo("350");
    assertThat(s.balance()).isEqualByComparingTo("650");
    assertThat(s.transactionCount()).isEqualTo(3);
  }

  @Test
  void monthly_returns_12_continuous_points_with_current_marked() throws Exception {
    Ctx a = signup("dash-monthly@picsou.demo");
    tx(a.userId(), null, "100", LocalDate.now(), TransactionType.expense);
    tx(a.userId(), null, "200", LocalDate.now().minusMonths(2), TransactionType.expense);

    MonthlyPoint[] points =
        getJson("/api/dashboard/charts/monthly", a.token(), MonthlyPoint[].class);
    assertThat(points).hasSize(12);
    assertThat(points[11].current()).isTrue();
    assertThat(points[11].total()).isEqualByComparingTo("100");
    // mois manquants remplis à 0
    assertThat(points).allSatisfy(p -> assertThat(p.total()).isNotNull());
  }

  @Test
  void category_breakdown_sorted_desc_with_resolved_names() throws Exception {
    Ctx a = signup("dash-breakdown@picsou.demo");
    List<Category> defaults = cats.findByUserIdIsNullOrUserId(a.userId());
    Category catA = defaults.get(0);
    Category catB = defaults.get(1);
    tx(a.userId(), catA.getId(), "300", LocalDate.now(), TransactionType.expense);
    tx(a.userId(), catB.getId(), "100", LocalDate.now(), TransactionType.expense);

    CategorySlice[] slices =
        getJson("/api/dashboard/charts/category-breakdown", a.token(), CategorySlice[].class);
    assertThat(slices).hasSizeGreaterThanOrEqualTo(2);
    assertThat(slices[0].category()).isEqualTo(catA.getName());
    assertThat(slices[0].total()).isEqualByComparingTo("300");
    assertThat(slices[1].category()).isEqualTo(catB.getName());
    assertThat(slices[1].total()).isEqualByComparingTo("100");
  }
}
