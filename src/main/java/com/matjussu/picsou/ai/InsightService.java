package com.matjussu.picsou.ai;

import com.matjussu.picsou.ai.dto.InsightResponse;
import com.matjussu.picsou.category.Category;
import com.matjussu.picsou.category.CategoryRepository;
import com.matjussu.picsou.transaction.CategoryAggRow;
import com.matjussu.picsou.transaction.TransactionRepository;
import com.matjussu.picsou.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Insights IA du mois : faits chiffrés calculés par NOUS (anti-confabulation) + prose FR du LLM,
 * avec cache 24h sur {@code ai_insights}.
 */
@Service
@RequiredArgsConstructor
public class InsightService {

  private static final String UNCATEGORIZED = "Sans catégorie";
  private static final int TOP_CATEGORIES = 5;
  private static final Duration CACHE_TTL = Duration.ofHours(24);
  private static final String PROMPT_VARIANT = "monthly-v1";

  private final TransactionRepository transactions;
  private final CategoryRepository categories;
  private final AiInsightRepository insights;
  private final AiClient aiClient;

  @Transactional
  public InsightResponse monthly(UUID userId) {
    YearMonth current = YearMonth.now();
    LocalDate start = current.atDay(1);
    LocalDate end = current.atEndOfMonth();
    InsightFacts facts = computeFacts(userId, start, end);

    // Cache 24h : réutilise la dernière prose de la période si encore fraîche.
    var cached =
        insights
            .findFirstByUserIdAndPeriodStartAndPeriodEndOrderByCreatedAtDesc(userId, start, end)
            .filter(
                i ->
                    i.getCreatedAt() != null
                        && i.getCreatedAt().isAfter(Instant.now().minus(CACHE_TTL)));
    if (cached.isPresent()) {
      AiInsight hit = cached.get();
      return new InsightResponse(
          start, end, hit.getResponse(), hit.getModelUsed(), hit.getTokensUsed(), true, facts);
    }

    // Sinon : appel LLM (peut lever AiUnavailableException → 503) + stockage.
    InsightResult result = aiClient.writeMonthlyInsight(facts);
    insights.save(
        AiInsight.builder()
            .userId(userId)
            .periodStart(start)
            .periodEnd(end)
            .promptVariant(PROMPT_VARIANT)
            .response(result.text())
            .tokensUsed(result.tokensUsed())
            .modelUsed(result.model())
            .build());
    return new InsightResponse(
        start, end, result.text(), result.model(), result.tokensUsed(), false, facts);
  }

  private InsightFacts computeFacts(UUID userId, LocalDate start, LocalDate end) {
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

  private static BigDecimal nz(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v;
  }
}
