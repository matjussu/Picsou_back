package com.matjussu.picsou.ai;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Faits chiffrés du mois, <b>calculés par nos agrégats</b>. Fournis au LLM pour qu'il rédige la
 * prose (il n'invente aucun chiffre) ET renvoyés au front pour les StatChips.
 */
public record InsightFacts(
    LocalDate periodStart,
    LocalDate periodEnd,
    BigDecimal income,
    BigDecimal expense,
    BigDecimal net,
    int transactionCount,
    List<CategoryShare> topCategories) {}
