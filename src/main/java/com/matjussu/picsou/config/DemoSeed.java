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
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
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
 * <p>User principal <b>Matteo</b> (matteo@picsou.demo / Demo-Password-123), entièrement anonymisé —
 * aucune donnée bancaire réelle (garde-fou #4). <b>Jamais via Flyway</b> (ne pas polluer la prod
 * Supabase). Idempotent <b>par-user</b> : chaque user n'est (re)créé que si son email est absent —
 * robuste à un état partiel (un redeploy ayant seedé un sous-ensemble des users ne fait pas crasher
 * le seed sur la contrainte d'email unique) et aux réveils Render (free tier) qui rejouent le
 * {@link CommandLineRunner}.
 *
 * <p>Stratégie de densité, alignée sur ce que lit chaque endpoint dashboard :
 *
 * <ul>
 *   <li>{@code summary} = mois courant → le mois en cours est dense (revenus + dépenses sur ~12
 *       catégories) pour des KPIs non triviaux. Toutes les dates du mois courant sont clampées à
 *       {@code <= today} : jamais de transaction future (qui ressemblerait à un bug en démo).
 *   <li>{@code monthly} = 12 derniers mois → 6 mois peuplés (3 récents denses + 3 plus anciens en
 *       récurrent allégé) pour un graphe mensuel visiblement rempli.
 *   <li>{@code categoryBreakdown} = mois courant par catégorie → le mois courant balaie la majorité
 *       des 15 catégories Flyway.
 * </ul>
 *
 * <p>Lancer : {@code SPRING_PROFILES_ACTIVE=demo} (ou {@code mvn spring-boot:run
 * -Dspring-boot.run.profiles=demo}).
 */
@Component
@Profile("demo")
@RequiredArgsConstructor
public class DemoSeed implements CommandLineRunner {

  private static final String MATTEO = "matteo@picsou.demo";
  private static final String MARIE = "marie@picsou.demo";
  private static final String PIERRE = "pierre@picsou.demo";
  private static final String DEMO_PASSWORD = "Demo-Password-123";

  /** Nb de mois récents entièrement densifiés (toutes catégories). */
  private static final int DENSE_MONTHS = 3;

  /** Nb de mois récents au total alimentés (denses + récurrent allégé) pour remplir le graphe. */
  private static final int FILLED_MONTHS = 6;

  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final AccountRepository accounts;
  private final CategoryRepository categories;
  private final TransactionRepository transactions;
  private final GoalRepository goals;
  private final GoalContributionRepository contributions;

  private final LocalDate today = LocalDate.now();

  @Override
  @Transactional
  public void run(String... args) {
    // Idempotent par-user : chaque user (+ sa data) est sauté si son email existe déjà.
    seedUser(MATTEO, "Matteo", this::seedMatteo);
    // Users secondaires : login multi-user démontrable. Marie = jeu allégé, Pierre = vierge.
    seedUser(MARIE, "Marie", this::seedMarie);
    seedUser(PIERRE, "Pierre", id -> {});
  }

  /** (Re)crée le user et seede sa data uniquement si l'email est absent. */
  private void seedUser(String email, String firstName, Consumer<UUID> dataSeeder) {
    if (users.existsByEmail(email)) {
      return; // déjà seedé — on saute le user et sa data
    }
    User user = createUser(email, firstName);
    dataSeeder.accept(user.getId());
  }

  // ── User principal : dense et crédible ──

  private void seedMatteo(UUID userId) {
    UUID courant = account(userId, "Compte courant", AccountType.bank, "2480.55");
    UUID epargne = account(userId, "Livret épargne", AccountType.bank, "4200.00");
    UUID especes = account(userId, "Espèces", AccountType.cash, "65.00");

    Map<String, UUID> cat = categoriesOf(userId);

    for (int back = 0; back < FILLED_MONTHS; back++) {
      boolean dense = back < DENSE_MONTHS;

      // ── Revenus ──
      tx(
          userId,
          courant,
          cat.get("Salaire"),
          2200,
          at(back, 2),
          TransactionType.income,
          "Alternance Capgemini");
      tx(
          userId,
          courant,
          cat.get("Bourse / BAF"),
          150,
          at(back, 5),
          TransactionType.income,
          "Bourse CROUS");
      if (dense && back % 2 == 0) {
        tx(
            userId,
            courant,
            cat.get("Virement reçu"),
            60,
            at(back, 12),
            TransactionType.income,
            "Remboursement entre amis");
      }
      if (dense && back % 2 == 1) {
        tx(
            userId,
            courant,
            cat.get("Remboursement"),
            24.90,
            at(back, 18),
            TransactionType.income,
            "Remboursement Sécurité sociale");
      }

      // ── Dépenses récurrentes (présentes tous les mois alimentés) ──
      tx(
          userId,
          courant,
          cat.get("Loyer"),
          750,
          at(back, 3),
          TransactionType.expense,
          "Loyer studio");
      tx(
          userId,
          courant,
          cat.get("Transport"),
          88.80,
          at(back, 4),
          TransactionType.expense,
          "Pass Navigo");
      tx(
          userId,
          courant,
          cat.get("Streaming & Abonnements"),
          13.49,
          at(back, 5),
          TransactionType.expense,
          "Netflix");
      tx(
          userId,
          courant,
          cat.get("Streaming & Abonnements"),
          10.99,
          at(back, 5),
          TransactionType.expense,
          "Spotify Premium");
      tx(
          userId,
          courant,
          cat.get("Courses"),
          102 + back * 6.0,
          at(back, 8),
          TransactionType.expense,
          "Courses Monoprix");
      tx(
          userId,
          courant,
          cat.get("Courses"),
          78 + back * 4.0,
          at(back, 22),
          TransactionType.expense,
          "Courses Lidl");

      if (!dense) {
        continue; // mois anciens : récurrent seulement (remplit le graphe sans surcharge)
      }

      // ── Mois récents : variété pour breakdown + liste filtrable ──
      tx(
          userId,
          courant,
          cat.get("Restaurant"),
          28 + back * 3.0,
          at(back, 11),
          TransactionType.expense,
          "Déjeuner midi");
      tx(
          userId,
          courant,
          cat.get("Restaurant"),
          44 + back * 5.0,
          at(back, 19),
          TransactionType.expense,
          "Dîner entre amis");
      tx(
          userId,
          courant,
          cat.get("Loisirs"),
          22 + back * 4.0,
          at(back, 15),
          TransactionType.expense,
          "Cinéma");
      tx(
          userId,
          especes,
          cat.get("Autre"),
          12.50,
          at(back, 6),
          TransactionType.expense,
          "Boulangerie");

      // Rotation pour couvrir les catégories restantes au fil des mois.
      switch (back) {
        case 0 -> {
          tx(
              userId,
              courant,
              cat.get("Santé"),
              32,
              at(0, 17),
              TransactionType.expense,
              "Pharmacie");
          tx(
              userId,
              courant,
              cat.get("Vêtements"),
              59.90,
              at(0, 20),
              TransactionType.expense,
              "Uniqlo");
          tx(
              userId,
              courant,
              cat.get("Éducation"),
              29.99,
              at(0, 23),
              TransactionType.expense,
              "Abonnement Udemy");
          tx(
              userId,
              courant,
              cat.get("Cadeaux"),
              48,
              at(0, 25),
              TransactionType.expense,
              "Cadeau anniversaire");
        }
        case 1 -> {
          tx(
              userId,
              courant,
              cat.get("Éducation"),
              39,
              at(1, 23),
              TransactionType.expense,
              "Manuel de cours");
          tx(
              userId,
              courant,
              cat.get("Cadeaux"),
              35,
              at(1, 25),
              TransactionType.expense,
              "Cadeau fête des mères");
          tx(
              userId,
              especes,
              cat.get("Loisirs"),
              18,
              at(1, 9),
              TransactionType.expense,
              "Sortie bowling");
        }
        case 2 -> {
          tx(
              userId,
              courant,
              cat.get("Santé"),
              26,
              at(2, 14),
              TransactionType.expense,
              "Consultation médecin");
          tx(
              userId,
              courant,
              cat.get("Vêtements"),
              75,
              at(2, 21),
              TransactionType.expense,
              "Manteau d'hiver");
        }
        default -> {
          /* no extra rotation */
        }
      }
    }

    // ── Épargne mensuelle : virement vers le livret (revenu côté livret) ──
    for (int back = 0; back < DENSE_MONTHS; back++) {
      tx(
          userId,
          epargne,
          cat.get("Virement reçu"),
          200,
          at(back, 6),
          TransactionType.income,
          "Virement épargne mensuel");
    }

    // ── Goals à divers stades (early / mid / advanced / completed) ──
    seedGoal(
        userId,
        "Permis bateau",
        600,
        GoalTemplate.custom,
        today.plusMonths(6),
        new double[] {80, 70}); // ~25 %
    seedGoal(
        userId,
        "Fonds d'urgence",
        5000,
        GoalTemplate.savings,
        null,
        new double[] {800, 700, 600}); // ~42 %
    seedGoal(
        userId,
        "Vacances Italie",
        1500,
        GoalTemplate.travel,
        today.plusMonths(3),
        new double[] {400, 300, 200}); // 60 %
    seedGoal(
        userId,
        "MacBook Pro",
        1800,
        GoalTemplate.purchase,
        null,
        new double[] {600, 600, 600}); // 100 % → completed
  }

  // ── User secondaire allégé (login multi-user crédible, pas le focus de la démo) ──

  private void seedMarie(UUID userId) {
    UUID courant = account(userId, "Compte courant", AccountType.bank, "920.00");
    Map<String, UUID> cat = categoriesOf(userId);
    for (int back = 0; back < 2; back++) {
      tx(
          userId,
          courant,
          cat.get("Salaire"),
          1200,
          at(back, 2),
          TransactionType.income,
          "Alternance");
      tx(
          userId,
          courant,
          cat.get("Loyer"),
          500,
          at(back, 4),
          TransactionType.expense,
          "Loyer (part Marie)");
      tx(
          userId,
          courant,
          cat.get("Courses"),
          96.30,
          at(back, 10),
          TransactionType.expense,
          "Courses");
      tx(userId, courant, cat.get("Transport"), 75, at(back, 3), TransactionType.expense, "Navigo");
      tx(
          userId,
          courant,
          cat.get("Streaming & Abonnements"),
          13.49,
          at(back, 5),
          TransactionType.expense,
          "Netflix");
    }
    seedGoal(
        userId,
        "Week-end Lisbonne",
        700,
        GoalTemplate.travel,
        today.plusMonths(2),
        new double[] {250, 150}); // ~57 %
  }

  // ── Helpers ──

  private User createUser(String email, String firstName) {
    return users.save(
        User.builder()
            .email(email)
            .passwordHash(encoder.encode(DEMO_PASSWORD))
            .firstName(firstName)
            .build());
  }

  private UUID account(UUID userId, String name, AccountType type, String balance) {
    return accounts
        .save(
            Account.builder()
                .userId(userId)
                .name(name)
                .type(type)
                .balance(new BigDecimal(balance))
                .build())
        .getId();
  }

  private Map<String, UUID> categoriesOf(UUID userId) {
    return categories.findByUserIdIsNullOrUserId(userId).stream()
        .collect(Collectors.toMap(Category::getName, Category::getId, (a, b) -> a));
  }

  /**
   * Date au mois {@code back} (0 = courant) et au jour demandé, bornée à la longueur du mois et,
   * pour le mois courant, à aujourd'hui — jamais de transaction future.
   */
  private LocalDate at(int back, int day) {
    LocalDate firstOfMonth = today.minusMonths(back).withDayOfMonth(1);
    int clampedDay = Math.min(day, firstOfMonth.lengthOfMonth());
    LocalDate date = firstOfMonth.withDayOfMonth(clampedDay);
    return date.isAfter(today) ? today : date;
  }

  private void tx(
      UUID userId,
      UUID accountId,
      UUID categoryId,
      double amount,
      LocalDate date,
      TransactionType type,
      String description) {
    transactions.save(
        Transaction.builder()
            .userId(userId)
            .accountId(accountId)
            .categoryId(categoryId)
            .amount(money(amount))
            .date(date)
            .description(description)
            .type(type)
            .build());
  }

  private void seedGoal(
      UUID userId,
      String name,
      double target,
      GoalTemplate template,
      LocalDate deadline,
      double[] contribs) {
    BigDecimal targetAmount = money(target);
    BigDecimal current = BigDecimal.ZERO;
    for (double c : contribs) {
      current = current.add(money(c));
    }
    Goal goal =
        goals.save(
            Goal.builder()
                .userId(userId)
                .name(name)
                .targetAmount(targetAmount)
                .currentAmount(current)
                .deadline(deadline)
                .template(template)
                .completedAt(current.compareTo(targetAmount) >= 0 ? Instant.now() : null)
                .build());
    // Contributions étalées mois par mois jusqu'à aujourd'hui (cohérent avec currentAmount).
    LocalDate d = today.minusMonths(contribs.length);
    for (double c : contribs) {
      contributions.save(
          GoalContribution.builder().goalId(goal.getId()).amount(money(c)).date(d).build());
      d = d.plusMonths(1);
    }
  }

  private static BigDecimal money(double value) {
    return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
  }
}
