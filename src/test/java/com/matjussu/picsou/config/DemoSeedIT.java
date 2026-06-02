package com.matjussu.picsou.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.matjussu.picsou.account.AccountRepository;
import com.matjussu.picsou.coloc.ColocBalanceService;
import com.matjussu.picsou.coloc.ColocGroupRepository;
import com.matjussu.picsou.coloc.ColocMemberRepository;
import com.matjussu.picsou.coloc.SharedExpenseService;
import com.matjussu.picsou.coloc.dto.BalanceResponse;
import com.matjussu.picsou.coloc.dto.SharedExpenseResponse;
import com.matjussu.picsou.goal.GoalRepository;
import com.matjussu.picsou.transaction.TransactionRepository;
import com.matjussu.picsou.user.User;
import com.matjussu.picsou.user.UserRepository;
import java.util.List;
import java.util.UUID;
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
  @Autowired ColocGroupRepository colocGroups;
  @Autowired ColocMemberRepository colocMembers;
  @Autowired ColocBalanceService balanceService;
  @Autowired SharedExpenseService sharedExpenseService;
  @Autowired DemoSeed demoSeed;

  @Test
  void seed_populates_matteo_densely() {
    User matteo = users.findByEmail("matteo@picsou.demo").orElseThrow();

    // User principal dense : 3 comptes perso + 1 compte coloc (« Le Loft »), gros volume de
    // transactions, goals variés.
    assertThat(accounts.findByUserId(matteo.getId())).hasSize(4);
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
    // État partiel : un user secondaire manque, les autres sont là. Le guard par-user doit
    // recréer le manquant sans dupliquer ni crasher sur les présents. (On supprime Marie et non
    // Matteo : Matteo est créateur du groupe coloc + payeur, références FK sans ON DELETE CASCADE.)
    User marie = users.findByEmail("marie@picsou.demo").orElseThrow();
    users.deleteById(marie.getId()); // ON DELETE CASCADE → ses accounts/tx/goals
    long usersWithoutMarie = users.count();

    demoSeed.run(); // ne doit pas crasher malgré Matteo/Pierre déjà présents

    assertThat(users.findByEmail("marie@picsou.demo")).isPresent(); // Marie recréée
    assertThat(users.findByEmail("matteo@picsou.demo")).isPresent(); // Matteo intact
    assertThat(users.count()).isEqualTo(usersWithoutMarie + 1); // aucun doublon
  }

  @Test
  void seed_coloc_le_loft_two_transfers_and_idempotent() {
    UUID matteo = users.findByEmail("matteo@picsou.demo").orElseThrow().getId();

    // Groupe « Le Loft » avec Matteo + Titouan + Hugo.
    var memberships = colocMembers.findByUserId(matteo);
    assertThat(memberships).hasSize(1);
    UUID groupId = memberships.get(0).getColocGroupId();
    assertThat(colocGroups.findById(groupId)).get().extracting("name").isEqualTo("Le Loft");
    assertThat(colocMembers.findByColocGroupId(groupId)).hasSize(3);
    assertThat(users.findByEmail("titouan@picsou.demo")).isPresent();
    assertThat(users.findByEmail("hugo@picsou.demo")).isPresent();

    // Bilan calibré : EXACTEMENT 2 virements.
    BalanceResponse balances = balanceService.balances(matteo, groupId);
    assertThat(balances.transfers()).hasSize(2);

    // Au moins une dépense déjà réglée.
    List<SharedExpenseResponse> expenses = sharedExpenseService.list(matteo, groupId);
    assertThat(expenses).anyMatch(SharedExpenseResponse::settled);

    // Idempotence GROUPE : re-run → pas de nouveau groupe ni doublon de membership.
    long groupsBefore = colocGroups.count();
    demoSeed.run();
    assertThat(colocGroups.count()).isEqualTo(groupsBefore);
    assertThat(colocMembers.findByUserId(matteo)).hasSize(1);
  }
}
