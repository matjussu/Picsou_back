package com.matjussu.picsou.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.matjussu.picsou.goal.GoalRepository;
import com.matjussu.picsou.transaction.TransactionRepository;
import com.matjussu.picsou.user.User;
import com.matjussu.picsou.user.UserRepository;
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
 * Vérifie le seed démo de façon déterministe (profil demo actif → le CommandLineRunner s'exécute au
 * démarrage du contexte), sans dépendre d'un spring-boot:run manuel contre Supabase.
 */
@SpringBootTest
@ActiveProfiles({"test", "demo"})
@Testcontainers
class DemoSeedIT {

  @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", pg::getJdbcUrl);
    r.add("spring.datasource.username", pg::getUsername);
    r.add("spring.datasource.password", pg::getPassword);
  }

  @Autowired UserRepository users;
  @Autowired TransactionRepository transactions;
  @Autowired GoalRepository goals;
  @Autowired DemoSeed demoSeed;

  @Test
  void seed_populates_marie_and_is_idempotent() {
    User marie = users.findByEmail("marie@picsou.demo").orElseThrow();
    assertThat(transactions.count()).isGreaterThan(0);
    assertThat(goals.findByUserId(marie.getId())).hasSize(2);

    long usersBefore = users.count();
    long txBefore = transactions.count();

    demoSeed.run(); // re-run : doit être idempotent (garde existsByEmail)

    assertThat(users.count()).isEqualTo(usersBefore);
    assertThat(transactions.count()).isEqualTo(txBefore);
  }
}
