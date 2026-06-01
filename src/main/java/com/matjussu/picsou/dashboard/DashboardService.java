package com.matjussu.picsou.dashboard;

import com.matjussu.picsou.category.Category;
import com.matjussu.picsou.category.CategoryRepository;
import com.matjussu.picsou.dashboard.dto.CategorySlice;
import com.matjussu.picsou.dashboard.dto.DashboardSummary;
import com.matjussu.picsou.dashboard.dto.MonthlyPoint;
import com.matjussu.picsou.transaction.MonthlyAggRow;
import com.matjussu.picsou.transaction.TransactionRepository;
import com.matjussu.picsou.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardService {

  private static final int MONTHS = 12;
  private static final String UNCATEGORIZED = "Sans catégorie";

  private final TransactionRepository transactions;
  private final CategoryRepository categories;

  /** KPIs du mois courant. */
  public DashboardSummary summary(UUID userId) {
    YearMonth now = YearMonth.now();
    LocalDate from = now.atDay(1);
    LocalDate to = now.atEndOfMonth();
    BigDecimal income =
        transactions.sumByTypeAndPeriod(userId, TransactionType.income, from, to);
    BigDecimal expense =
        transactions.sumByTypeAndPeriod(userId, TransactionType.expense, from, to);
    int count = (int) transactions.countByUserIdAndDateBetween(userId, from, to);
    return new DashboardSummary(income, expense, income.subtract(expense), count);
  }

  /** Série des dépenses sur les 12 derniers mois — 12 points continus (mois manquants à 0). */
  public List<MonthlyPoint> monthly(UUID userId) {
    YearMonth current = YearMonth.now();
    YearMonth start = current.minusMonths(MONTHS - 1L);
    Map<String, BigDecimal> byPeriod =
        transactions.monthlyExpenseSeries(userId, start.atDay(1)).stream()
            .collect(
                Collectors.toMap(
                    MonthlyAggRow::getPeriod,
                    r -> r.getTotal() == null ? BigDecimal.ZERO : r.getTotal()));
    List<MonthlyPoint> points = new ArrayList<>(MONTHS);
    for (int i = 0; i < MONTHS; i++) {
      YearMonth ym = start.plusMonths(i);
      String key = ym.toString(); // 'YYYY-MM', aligné sur to_char(...,'YYYY-MM')
      points.add(
          new MonthlyPoint(key, byPeriod.getOrDefault(key, BigDecimal.ZERO), ym.equals(current)));
    }
    return points;
  }

  /** Répartition des dépenses du mois courant par catégorie (noms résolus, triée desc). */
  public List<CategorySlice> categoryBreakdown(UUID userId) {
    YearMonth now = YearMonth.now();
    Map<UUID, String> names =
        categories.findByUserIdIsNullOrUserId(userId).stream()
            .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));
    return transactions
        .breakdownByCategory(userId, TransactionType.expense, now.atDay(1), now.atEndOfMonth())
        .stream()
        .map(
            r ->
                new CategorySlice(
                    r.getCategoryId() == null
                        ? UNCATEGORIZED
                        : names.getOrDefault(r.getCategoryId(), UNCATEGORIZED),
                    r.getTotal() == null ? BigDecimal.ZERO : r.getTotal()))
        .toList();
  }
}
