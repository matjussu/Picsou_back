package com.matjussu.picsou.ai;

/**
 * Abstraction du fournisseur d'IA (prose d'insight). Seul impl prod : {@link AnthropicAiClient}.
 * L'interface existe surtout comme seam de test (les IT la stubent via {@code @MockBean}, jamais
 * d'appel à l'API réelle payante).
 */
public interface AiClient {

  /**
   * Rédige la prose FR du résumé mensuel à partir de faits chiffrés.
   *
   * @throws AiUnavailableException si l'IA n'est pas configurée (clé absente) → 503.
   */
  InsightResult writeMonthlyInsight(InsightFacts facts);

  /**
   * Répond en français à une question libre de l'utilisateur sur ses finances, en s'appuyant
   * UNIQUEMENT sur le contexte chiffré fourni (n'invente aucun montant).
   *
   * @throws AiUnavailableException si l'IA n'est pas configurée (clé absente) → 503.
   */
  InsightResult answerQuestion(AskContext context, String question);
}
