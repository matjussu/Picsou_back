package com.matjussu.picsou.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.matjussu.picsou.transaction.dto.TransactionFilter;
import com.matjussu.picsou.user.User;
import com.matjussu.picsou.user.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Cœur de la note : les filtres s'exécutent contre une vraie base Postgres (Testcontainers), ce qui
 * valide aussi le binding G2 (filtre par enum natif {@code tx_type}).
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class TransactionSpecificationsTest {

  @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", pg::getJdbcUrl);
    r.add("spring.datasource.username", pg::getUsername);
    r.add("spring.datasource.password", pg::getPassword);
  }

  @Autowired TransactionRepository repo;
  @Autowired UserRepository users;

  UUID userA;
  UUID userB;

  @BeforeEach
  void seed() {
    repo.deleteAll();
    users.deleteAll();
    userA = saveUser("spec-a@picsou.demo");
    userB = saveUser("spec-b@picsou.demo");

    repo.save(tx(userA, "100", "2026-05-01", "Salaire", TransactionType.income));
    repo.save(tx(userA, "20", "2026-05-10", "Resto", TransactionType.expense));
    repo.save(tx(userA, "50", "2026-05-20", "Courses", TransactionType.expense));
    repo.save(tx(userB, "999", "2026-05-15", "Autre", TransactionType.expense));
  }

  private UUID saveUser(String email) {
    return users
        .save(User.builder().email(email).passwordHash("x").firstName("Spec").build())
        .getId();
  }

  private Transaction tx(UUID userId, String amount, String date, String desc, TransactionType t) {
    return Transaction.builder()
        .userId(userId)
        .amount(new BigDecimal(amount))
        .date(LocalDate.parse(date))
        .description(desc)
        .type(t)
        .build();
  }

  private static TransactionFilter empty() {
    return new TransactionFilter(null, null, null, null, null, null, null, null);
  }

  @Test
  void user_scope_excludes_other_users() {
    assertThat(repo.findAll(TransactionSpecifications.withFilters(userA, empty()))).hasSize(3);
    assertThat(repo.findAll(TransactionSpecifications.withFilters(userB, empty()))).hasSize(1);
  }

  @Test
  void filters_by_date_from() {
    var f =
        new TransactionFilter(
            LocalDate.parse("2026-05-15"), null, null, null, null, null, null, null);
    assertThat(repo.findAll(TransactionSpecifications.withFilters(userA, f))).hasSize(1); // Courses
  }

  @Test
  void filters_by_type_expense() {
    var f =
        new TransactionFilter(null, null, null, null, null, null, TransactionType.expense, null);
    assertThat(repo.findAll(TransactionSpecifications.withFilters(userA, f))).hasSize(2); // G2
  }

  @Test
  void filters_by_text_query_case_insensitive() {
    var f = new TransactionFilter(null, null, null, null, null, null, null, "res");
    assertThat(repo.findAll(TransactionSpecifications.withFilters(userA, f))).hasSize(1); // Resto
  }

  @Test
  void filters_by_min_amount() {
    var f = new TransactionFilter(null, null, null, null, new BigDecimal("50"), null, null, null);
    assertThat(repo.findAll(TransactionSpecifications.withFilters(userA, f)))
        .hasSize(2); // 100 + 50
  }
}
