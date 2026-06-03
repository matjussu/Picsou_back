package com.matjussu.picsou.ai;

import com.matjussu.picsou.ai.dto.InsightResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Insights IA du mois : faits chiffrés calculés par NOUS (anti-confabulation, via {@link
 * FinancialFactsService}) + prose FR du LLM, avec cache 24h sur {@code ai_insights}.
 */
@Service
@RequiredArgsConstructor
public class InsightService {

  private static final Duration CACHE_TTL = Duration.ofHours(24);
  private static final String PROMPT_VARIANT = "monthly-v1";

  private final AiInsightRepository insights;
  private final AiClient aiClient;
  private final FinancialFactsService factsService;

  @Transactional
  public InsightResponse monthly(UUID userId) {
    YearMonth current = YearMonth.now();
    LocalDate start = current.atDay(1);
    LocalDate end = current.atEndOfMonth();
    InsightFacts facts = factsService.forPeriod(userId, start, end);

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
}
