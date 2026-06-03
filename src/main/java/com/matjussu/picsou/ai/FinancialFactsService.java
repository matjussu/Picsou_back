package com.matjussu.picsou.ai;

import com.matjussu.picsou.category.Category;
import com.matjussu.picsou.category.CategoryRepository;
import com.matjussu.picsou.transaction.CategoryAggRow;
import com.matjussu.picsou.transaction.TransactionRepository;
import com.matjussu.picsou.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Calcul des faits chiffrés financiers (anti-confabulation) à partir de nos agrégats. Source unique
 * partagée par {@link InsightService} (résumé mensuel) et le Q&A IA libre ({@link AskService}) :
 * aucun montant n'est jamais produit par le LLM.
 */
@Service
@RequiredArgsConstructor
public class FinancialFactsService {

  private static final String UNCATEGORIZED = "Sans catégorie";
  private static final int TOP_CATEGORIES = 5;

  private final TransactionRepository transactions;
  private final CategoryRepository categories;

  /** Faits du mois courant. */
  public InsightFacts currentMonth(UUID userId) {
    YearMonth current = YearMonth.now();
    return forPeriod(userId, current.atDay(1), current.atEndOfMonth());
  }

  /** Faits sur une période arbitraire [start, end]. */
  public InsightFacts forPeriod(UUID userId, LocalDate start, LocalDate end) {
    BigDecimal income =
        nz(transactions.sumByTypeAndPeriod(userId, TransactionType.income, start, end));
    BigDecimal expense =
        nz(transactions.sumByTypeAndPeriod(userId, TransactionType.expense, start, end));
    int count = (int) transactions.countByUserIdAndDateBetween(userId, start, end);

    Map<UUID, String> names =
        categories.findByUserIdIsNullOrUserId(userId).stream()
            .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));
    List<CategoryShare> top =
        transactions.breakdownByCategory(userId, TransactionType.expense, start, end).stream()
            .map(
                (CategoryAggRow r) ->
                    new CategoryShare(
                        r.getCategoryId() == null
                            ? UNCATEGORIZED
                            : names.getOrDefault(r.getCategoryId(), UNCATEGORIZED),
                        nz(r.getTotal())))
            .limit(TOP_CATEGORIES)
            .toList();

    return new InsightFacts(start, end, income, expense, income.subtract(expense), count, top);
  }

  static BigDecimal nz(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v;
  }
}
