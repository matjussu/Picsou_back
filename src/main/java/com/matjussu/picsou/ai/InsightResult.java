package com.matjussu.picsou.ai;

/** Sortie d'un appel LLM d'insight : prose FR + métriques de coût. */
public record InsightResult(String text, int tokensUsed, String model) {}
