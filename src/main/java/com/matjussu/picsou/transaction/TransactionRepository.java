package com.matjussu.picsou.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository
    extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

  Optional<Transaction> findByIdAndUserId(UUID id, UUID userId);

  boolean existsByUserIdAndCategoryId(UUID userId, UUID categoryId);

  /** Idempotence import Open Banking : ne pas réimporter une transaction déjà synchronisée. */
  boolean existsByExternalId(String externalId);

  /** Nombre de transactions rattachées à un compte (= transactions importées d'une connexion). */
  long countByAccountId(UUID accountId);

  long countByUserIdAndDateBetween(UUID userId, LocalDate from, LocalDate to);

  // ── Agrégations dashboard (user-scopées) ──

  /** Somme d'un type sur une période (G3 : enum passé en paramètre lié, pas en littéral JPQL). */
  @Query(
      "SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t "
          + "WHERE t.userId = :userId AND t.type = :type AND t.date BETWEEN :from AND :to")
  BigDecimal sumByTypeAndPeriod(
      @Param("userId") UUID userId,
      @Param("type") TransactionType type,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to);

  /** Répartition des dépenses par catégorie sur une période (JPQL GROUP BY, triée desc). */
  @Query(
      "SELECT t.categoryId AS categoryId, COALESCE(SUM(t.amount), 0) AS total FROM Transaction t "
          + "WHERE t.userId = :userId AND t.type = :type AND t.date BETWEEN :from AND :to "
          + "GROUP BY t.categoryId ORDER BY total DESC")
  List<CategoryAggRow> breakdownByCategory(
      @Param("userId") UUID userId,
      @Param("type") TransactionType type,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to);

  /**
   * Série mensuelle des dépenses (G2 : SQL natif, JPQL ne bucket pas par mois). {@code type =
   * 'expense'} est ici du SQL Postgres direct sur l'enum natif, pas du JPQL.
   */
  @Query(
      value =
          "SELECT to_char(date_trunc('month', date), 'YYYY-MM') AS period, "
              + "COALESCE(SUM(amount), 0) AS total FROM transactions "
              + "WHERE user_id = :userId AND type = 'expense' AND date >= :from "
              + "GROUP BY date_trunc('month', date) ORDER BY date_trunc('month', date)",
      nativeQuery = true)
  List<MonthlyAggRow> monthlyExpenseSeries(
      @Param("userId") UUID userId, @Param("from") LocalDate from);
}
