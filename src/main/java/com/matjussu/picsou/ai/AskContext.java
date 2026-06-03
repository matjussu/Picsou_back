package com.matjussu.picsou.ai;

import java.math.BigDecimal;
import java.util.List;

/**
 * Contexte financier fourni au LLM pour répondre à une question libre. <b>Tous les chiffres sont
 * calculés par nos agrégats</b> (anti-confabulation) : le LLM raisonne dessus mais n'invente aucun
 * montant. Borné volontairement (mois courant + solde total + objectifs + tendance récente) pour
 * couvrir des questions variées sans surcharge.
 *
 * @param month faits du mois courant (revenus/dépenses/solde/catégories)
 * @param totalBalance solde total cumulé des comptes
 * @param goals objectifs d'épargne (nom, cible, courant)
 * @param recentMonths dépenses totales des derniers mois (tendance)
 */
public record AskContext(
    InsightFacts month,
    BigDecimal totalBalance,
    List<GoalBrief> goals,
    List<MonthlyExpense> recentMonths) {

  /** Objectif d'épargne résumé. */
  public record GoalBrief(String name, BigDecimal target, BigDecimal current) {}

  /** Dépense totale d'un mois ({@code YYYY-MM}). */
  public record MonthlyExpense(String period, BigDecimal total) {}
}
