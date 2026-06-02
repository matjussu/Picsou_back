package com.matjussu.picsou.ai.dto;

import com.matjussu.picsou.ai.InsightFacts;
import java.time.LocalDate;

/**
 * Réponse insight : prose FR du LLM + faits chiffrés (pour les StatChips, calculés par nous) +
 * métadonnées de coût/cache.
 */
public record InsightResponse(
    LocalDate periodStart,
    LocalDate periodEnd,
    String text,
    String model,
    Integer tokensUsed,
    boolean cached,
    InsightFacts facts) {}
