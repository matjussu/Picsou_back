package com.matjussu.picsou.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.matjussu.picsou.account.AccountRepository;
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
  @Autowired AccountRepository accounts;
  @Autowired TransactionRepository transactions;
  @Autowired GoalRepository goals;
  @Autowired DemoSeed demoSeed;

  @Test
  void seed_populates_matteo_densely() {
    User matteo = users.findByEmail("matteo@picsou.demo").orElseThrow();

    // User principal dense : plusieurs comptes, gros volume de transactions, goals variés.
    assertThat(accounts.findByUserId(matteo.getId())).hasSize(3);
    assertThat(transactions.count()).isGreaterThan(40);
    assertThat(goals.findByUserId(matteo.getId())).hasSize(4);
    // Au moins un goal atteint (completedAt non nul) et au moins un en cours.
    assertThat(goals.findByUserIdAndCompletedAtIsNotNull(matteo.getId())).isNotEmpty();
    assertThat(goals.findByUserIdAndCompletedAtIsNull(matteo.getId())).isNotEmpty();

    // Users secondaires présents (login multi-user démontrable).
    assertThat(users.findByEmail("marie@picsou.demo")).isPresent();
    assertThat(users.findByEmail("pierre@picsou.demo")).isPresent();
  }

  @Test
  void seed_is_idempotent() {
    long usersBefore = users.count();
    long txBefore = transactions.count();
    long goalsBefore = goals.count();

    demoSeed.run(); // re-run : doit être idempotent (garde par-user)

    assertThat(users.count()).isEqualTo(usersBefore);
    assertThat(transactions.count()).isEqualTo(txBefore);
    assertThat(goals.count()).isEqualTo(goalsBefore);
  }

  @Test
  void seed_is_robust_to_partial_state() {
    // Simule un redeploy partiel : Matteo supprimé (sa data part en cascade DB),
    // Marie + Pierre restent. Un guard MATTEO-only re-créerait Matteo PUIS crasherait
    // sur createUser(MARIE) (email unique). Le guard par-user doit no-op sur Marie/Pierre.
    User matteo = users.findByEmail("matteo@picsou.demo").orElseThrow();
    users.deleteById(matteo.getId()); // ON DELETE CASCADE → accounts/tx/goals/contribs
    long usersWithoutMatteo = users.count();

    demoSeed.run(); // ne doit pas crasher malgré Marie/Pierre déjà présents

    assertThat(users.findByEmail("matteo@picsou.demo")).isPresent(); // Matteo recréé
    assertThat(users.findByEmail("marie@picsou.demo")).isPresent(); // Marie intacte
    assertThat(users.count()).isEqualTo(usersWithoutMatteo + 1); // aucun doublon
  }
}
