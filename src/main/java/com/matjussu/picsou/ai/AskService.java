package com.matjussu.picsou.ai;

import com.matjussu.picsou.account.Account;
import com.matjussu.picsou.account.AccountRepository;
import com.matjussu.picsou.ai.AskContext.GoalBrief;
import com.matjussu.picsou.ai.AskContext.MonthlyExpense;
import com.matjussu.picsou.ai.dto.AskResponse;
import com.matjussu.picsou.goal.GoalRepository;
import com.matjussu.picsou.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Q&A IA libre : l'utilisateur pose une question sur SES finances, le LLM répond en s'appuyant
 * UNIQUEMENT sur le contexte chiffré qu'on lui fournit ({@link AskContext}, anti-confabulation).
 * Pas de cache (questions variées). Réutilise l'infra insights ({@link AiClient}, 503 propre).
 */
@Service
@RequiredArgsConstructor
public class AskService {

  /** Nombre de mois de tendance de dépenses fournis au contexte. */
  private static final int TREND_MONTHS = 6;

  private final FinancialFactsService factsService;
  private final AccountRepository accounts;
  private final GoalRepository goals;
  private final TransactionRepository transactions;
  private final AiClient aiClient;

  @Transactional(readOnly = true)
  public AskResponse ask(UUID userId, String question) {
    AskContext context = buildContext(userId);
    InsightResult result = aiClient.answerQuestion(context, question);
    return new AskResponse(result.text(), result.model(), result.tokensUsed());
  }

  /** Agrège le contexte financier user-scopé fourni au LLM (chiffres fiables, jamais inventés). */
  private AskContext buildContext(UUID userId) {
    InsightFacts month = factsService.currentMonth(userId);

    BigDecimal totalBalance =
        accounts.findByUserId(userId).stream()
            .map(Account::getBalance)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    List<GoalBrief> goalBriefs =
        goals.findByUserId(userId).stream()
            .map(g -> new GoalBrief(g.getName(), g.getTargetAmount(), g.getCurrentAmount()))
            .toList();

    LocalDate from = LocalDate.now().withDayOfMonth(1).minusMonths(TREND_MONTHS - 1L);
    List<MonthlyExpense> recentMonths =
        transactions.monthlyExpenseSeries(userId, from).stream()
            .map(r -> new MonthlyExpense(r.getPeriod(), r.getTotal()))
            .toList();

    return new AskContext(month, totalBalance, goalBriefs, recentMonths);
  }
}
