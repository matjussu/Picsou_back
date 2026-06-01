package com.matjussu.picsou.config;

import com.matjussu.picsou.account.Account;
import com.matjussu.picsou.account.AccountRepository;
import com.matjussu.picsou.account.AccountType;
import com.matjussu.picsou.category.Category;
import com.matjussu.picsou.category.CategoryRepository;
import com.matjussu.picsou.goal.Goal;
import com.matjussu.picsou.goal.GoalContribution;
import com.matjussu.picsou.goal.GoalContributionRepository;
import com.matjussu.picsou.goal.GoalRepository;
import com.matjussu.picsou.goal.GoalTemplate;
import com.matjussu.picsou.transaction.Transaction;
import com.matjussu.picsou.transaction.TransactionRepository;
import com.matjussu.picsou.transaction.TransactionType;
import com.matjussu.picsou.user.User;
import com.matjussu.picsou.user.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Jeu de démo (profil "demo" uniquement) pour peupler dashboard + goals à la soutenance.
 *
 * <p>Profil Marie (design §8) anonymisé — aucune donnée bancaire réelle. <b>Jamais via Flyway</b>
 * (ne pas polluer la prod Supabase). Idempotent : ne reseed pas si Marie existe déjà.
 *
 * <p>Lancer : {@code mvn spring-boot:run -Dspring-boot.run.profiles=demo} (ou {@code
 * SPRING_PROFILES_ACTIVE=demo}). Login : marie@picsou.demo / Demo-Password-123.
 */
@Component
@Profile("demo")
@RequiredArgsConstructor
public class DemoSeed implements CommandLineRunner {

  private static final String MARIE = "marie@picsou.demo";
  private static final String DEMO_PASSWORD = "Demo-Password-123";

  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final AccountRepository accounts;
  private final CategoryRepository categories;
  private final TransactionRepository transactions;
  private final GoalRepository goals;
  private final GoalContributionRepository contributions;

  @Override
  @Transactional
  public void run(String... args) {
    if (users.existsByEmail(MARIE)) {
      return; // idempotent — déjà seedé
    }

    User marie =
        users.save(
            User.builder()
                .email(MARIE)
                .passwordHash(encoder.encode(DEMO_PASSWORD))
                .firstName("Marie")
                .build());
    users.save(
        User.builder()
            .email("pierre@picsou.demo")
            .passwordHash(encoder.encode(DEMO_PASSWORD))
            .firstName("Pierre")
            .build());

    UUID courant =
        accounts
            .save(
                Account.builder()
                    .userId(marie.getId())
                    .name("Compte courant")
                    .type(AccountType.cash)
                    .build())
            .getId();
    accounts.save(
        Account.builder().userId(marie.getId()).name("Espèces").type(AccountType.cash).build());

    Map<String, UUID> cat =
        categories.findByUserIdIsNullOrUserId(marie.getId()).stream()
            .collect(Collectors.toMap(Category::getName, Category::getId, (a, b) -> a));

    UUID m = marie.getId();
    // 3 mois (courant, -1, -2) → charts peuplés + breakdown varié.
    for (int back = 0; back <= 2; back++) {
      LocalDate base = LocalDate.now().minusMonths(back).withDayOfMonth(1);
      tx(
          m,
          courant,
          cat.get("Salaire"),
          "1200.00",
          base.withDayOfMonth(2),
          TransactionType.income,
          "Alternance Capgemini");
      tx(
          m,
          courant,
          cat.get("Loyer"),
          "500.00",
          base.withDayOfMonth(4),
          TransactionType.expense,
          "Loyer (part Marie)");
      tx(
          m,
          courant,
          cat.get("Transport"),
          "75.00",
          base.withDayOfMonth(3),
          TransactionType.expense,
          "Navigo");
      tx(
          m,
          courant,
          cat.get("Streaming & Abonnements"),
          "13.49",
          base.withDayOfMonth(5),
          TransactionType.expense,
          "Netflix");
      tx(
          m,
          courant,
          cat.get("Streaming & Abonnements"),
          "9.99",
          base.withDayOfMonth(5),
          TransactionType.expense,
          "Spotify");
      tx(
          m,
          courant,
          cat.get("Courses"),
          "118.40",
          base.withDayOfMonth(9),
          TransactionType.expense,
          "Monoprix");
      tx(
          m,
          courant,
          cat.get("Courses"),
          "76.20",
          base.withDayOfMonth(21),
          TransactionType.expense,
          "Courses Carrefour");
      tx(
          m,
          courant,
          cat.get("Restaurant"),
          "38.50",
          base.withDayOfMonth(14),
          TransactionType.expense,
          "Le Petit Bistrot");
    }
    // Atypiques du mois courant (pour un breakdown plus parlant).
    LocalDate now = LocalDate.now();
    tx(
        m,
        courant,
        cat.get("Restaurant"),
        "92.00",
        now,
        TransactionType.expense,
        "Anniversaire — resto");
    tx(m, courant, cat.get("Loisirs"), "59.99", now, TransactionType.expense, "Place de concert");

    seedGoal(
        m,
        "Vacances Italie",
        "800.00",
        GoalTemplate.travel,
        LocalDate.now().plusMonths(2),
        new String[] {"300.00", "200.00"});
    seedGoal(
        m,
        "Épargne précaution",
        "2000.00",
        GoalTemplate.savings,
        null,
        new String[] {"500.00", "400.00", "300.00"});
  }

  private void tx(
      UUID userId,
      UUID accountId,
      UUID categoryId,
      String amount,
      LocalDate date,
      TransactionType type,
      String description) {
    transactions.save(
        Transaction.builder()
            .userId(userId)
            .accountId(accountId)
            .categoryId(categoryId)
            .amount(new BigDecimal(amount))
            .date(date)
            .description(description)
            .type(type)
            .build());
  }

  private void seedGoal(
      UUID userId,
      String name,
      String target,
      GoalTemplate template,
      LocalDate deadline,
      String[] contribs) {
    BigDecimal current = BigDecimal.ZERO;
    for (String c : contribs) {
      current = current.add(new BigDecimal(c));
    }
    Goal goal =
        goals.save(
            Goal.builder()
                .userId(userId)
                .name(name)
                .targetAmount(new BigDecimal(target))
                .currentAmount(current)
                .deadline(deadline)
                .template(template)
                .build());
    LocalDate d = LocalDate.now().minusMonths(contribs.length);
    for (String c : contribs) {
      contributions.save(
          GoalContribution.builder()
              .goalId(goal.getId())
              .amount(new BigDecimal(c))
              .date(d)
              .build());
      d = d.plusMonths(1);
    }
  }
}
