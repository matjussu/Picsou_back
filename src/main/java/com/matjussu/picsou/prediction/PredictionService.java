package com.matjussu.picsou.prediction;

import com.matjussu.picsou.category.Category;
import com.matjussu.picsou.category.CategoryRepository;
import com.matjussu.picsou.prediction.dto.PredictionResponse;
import com.matjussu.picsou.transaction.CategoryAggRow;
import com.matjussu.picsou.transaction.TransactionRepository;
import com.matjussu.picsou.transaction.TransactionType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PredictionService {

  private static final String UNCATEGORIZED = "Sans catégorie";
  private static final int HISTORY_MONTHS = 3;

  private final TransactionRepository transactions;
  private final CategoryRepository categories;
  private final PredictionRepository predictions;

  /**
   * Calcule (algo pur), persiste (upsert par user+forecast_date) et renvoie la prédiction du mois.
   */
  @Transactional
  public PredictionResponse endOfMonth(UUID userId) {
    LocalDate today = LocalDate.now();
    YearMonth current = YearMonth.from(today);
    LocalDate monthStart = current.atDay(1);
    LocalDate forecastDate = current.atEndOfMonth();

    BigDecimal netSoFar =
        sum(userId, TransactionType.income, monthStart, today)
            .subtract(sum(userId, TransactionType.expense, monthStart, today));

    List<BigDecimal> historicalNets = new ArrayList<>();
    for (int i = 1; i <= HISTORY_MONTHS; i++) {
      YearMonth m = current.minusMonths(i);
      BigDecimal income = sum(userId, TransactionType.income, m.atDay(1), m.atEndOfMonth());
      BigDecimal expense = sum(userId, TransactionType.expense, m.atDay(1), m.atEndOfMonth());
      if (income.signum() != 0 || expense.signum() != 0) {
        historicalNets.add(income.subtract(expense));
      }
    }

    Map<UUID, String> categoryNames = categoryNames(userId);
    Map<String, BigDecimal> currentByCategory =
        expenseByCategory(userId, monthStart, today, categoryNames);
    Map<String, BigDecimal> historicalAvgByCategory =
        historicalAvgByCategory(userId, current, categoryNames);

    PredictionEngine.Result result =
        PredictionEngine.predict(
            netSoFar,
            today.getDayOfMonth(),
            current.lengthOfMonth(),
            historicalNets,
            currentByCategory,
            historicalAvgByCategory);

    upsert(userId, forecastDate, result);
    return new PredictionResponse(
        forecastDate, result.predictedBalance(), result.lowConfidence(), result.anomalies());
  }

  private void upsert(UUID userId, LocalDate forecastDate, PredictionEngine.Result result) {
    Prediction prediction =
        predictions
            .findByUserIdAndForecastDate(userId, forecastDate)
            .orElseGet(
                () -> Prediction.builder().userId(userId).forecastDate(forecastDate).build());
    prediction.setPredictedBalance(result.predictedBalance());
    prediction.setAnomalies(result.anomalies());
    predictions.save(prediction);
  }

  private BigDecimal sum(UUID userId, TransactionType type, LocalDate from, LocalDate to) {
    BigDecimal v = transactions.sumByTypeAndPeriod(userId, type, from, to);
    return v == null ? BigDecimal.ZERO : v;
  }

  private Map<UUID, String> categoryNames(UUID userId) {
    return categories.findByUserIdIsNullOrUserId(userId).stream()
        .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));
  }

  private Map<String, BigDecimal> expenseByCategory(
      UUID userId, LocalDate from, LocalDate to, Map<UUID, String> names) {
    Map<String, BigDecimal> byName = new HashMap<>();
    for (CategoryAggRow row :
        transactions.breakdownByCategory(userId, TransactionType.expense, from, to)) {
      String name =
          row.getCategoryId() == null
              ? UNCATEGORIZED
              : names.getOrDefault(row.getCategoryId(), UNCATEGORIZED);
      BigDecimal total = row.getTotal() == null ? BigDecimal.ZERO : row.getTotal();
      byName.merge(name, total, BigDecimal::add);
    }
    return byName;
  }

  /** Moyenne de dépense par catégorie sur les {@code HISTORY_MONTHS} mois précédents. */
  private Map<String, BigDecimal> historicalAvgByCategory(
      UUID userId, YearMonth current, Map<UUID, String> names) {
    Map<String, BigDecimal> totals = new HashMap<>();
    for (int i = 1; i <= HISTORY_MONTHS; i++) {
      YearMonth m = current.minusMonths(i);
      expenseByCategory(userId, m.atDay(1), m.atEndOfMonth(), names)
          .forEach((name, total) -> totals.merge(name, total, BigDecimal::add));
    }
    Map<String, BigDecimal> avg = new HashMap<>();
    totals.forEach(
        (name, total) ->
            avg.put(
                name, total.divide(BigDecimal.valueOf(HISTORY_MONTHS), 2, RoundingMode.HALF_UP)));
    return avg;
  }
}
