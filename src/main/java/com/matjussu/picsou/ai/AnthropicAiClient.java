package com.matjussu.picsou.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Impl prod de {@link AiClient} via l'API Anthropic Messages (RestClient HTTP brut, pas de SDK).
 *
 * <p>Lock dur anti-confabulation : on n'envoie que des <b>faits chiffrés</b> + une consigne stricte
 * « n'invente aucun chiffre ». Les montants ne sont jamais produits par le LLM. Prompt caching sur
 * le bloc système (coût). Clé jamais loggée/renvoyée ; absente → {@link AiUnavailableException}
 * (503).
 */
@Component
public class AnthropicAiClient implements AiClient {

  private static final String ANTHROPIC_VERSION = "2023-06-01";
  private static final int MAX_TOKENS = 1024;
  private static final String SYSTEM_PROMPT =
      "Tu es un coach financier personnel. Tu rédiges en français un résumé court (3 à 4 phrases),"
          + " clair et bienveillant, du mois budgétaire de l'utilisateur. Tu t'appuies UNIQUEMENT"
          + " sur les chiffres fournis : n'invente AUCUN montant, pourcentage ni statistique."
          + " N'ajoute pas de conseil d'investissement réglementé.";
  private static final String QA_SYSTEM_PROMPT =
      "Tu es un assistant financier personnel. Tu réponds en français, de façon claire et concise"
          + " (2 à 5 phrases), à la question de l'utilisateur sur SES finances. Tu t'appuies"
          + " UNIQUEMENT sur les chiffres fournis dans le contexte : n'invente AUCUN montant,"
          + " pourcentage ni statistique. Si la question porte sur une information absente du"
          + " contexte, dis-le honnêtement au lieu d'inventer. N'ajoute pas de conseil"
          + " d'investissement réglementé.";

  private final String apiKey;
  private final String model;
  private final RestClient rest;

  public AnthropicAiClient(
      @Value("${app.ai.anthropic-api-key:}") String apiKey,
      @Value("${app.ai.model:claude-sonnet-4-6}") String model,
      @Value("${app.ai.base-url:https://api.anthropic.com}") String baseUrl) {
    this.apiKey = apiKey;
    this.model = model;
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(10_000);
    factory.setReadTimeout(60_000); // la génération LLM peut être lente
    this.rest = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
  }

  @Override
  public InsightResult writeMonthlyInsight(InsightFacts facts) {
    return call(SYSTEM_PROMPT, userPrompt(facts));
  }

  @Override
  public InsightResult answerQuestion(AskContext context, String question) {
    return call(QA_SYSTEM_PROMPT, qaUserPrompt(context, question));
  }

  /**
   * Appel Messages API factorisé (anti-confab : seul le prompt système + les faits sont envoyés).
   */
  private InsightResult call(String systemPrompt, String userContent) {
    if (!StringUtils.hasText(apiKey)) {
      throw new AiUnavailableException("IA non configurée (clé Anthropic absente)");
    }
    Map<String, Object> body =
        Map.of(
            "model",
            model,
            "max_tokens",
            MAX_TOKENS,
            "system",
            List.of(
                Map.of(
                    "type",
                    "text",
                    "text",
                    systemPrompt,
                    "cache_control",
                    Map.of("type", "ephemeral"))),
            "messages",
            List.of(Map.of("role", "user", "content", userContent)));
    try {
      AnthropicResponse res =
          rest.post()
              .uri("/v1/messages")
              .header("x-api-key", apiKey)
              .header("anthropic-version", ANTHROPIC_VERSION)
              .body(body)
              .retrieve()
              .body(AnthropicResponse.class);
      if (res == null || res.content() == null || res.content().isEmpty()) {
        throw new AiUnavailableException("Réponse IA vide");
      }
      String text =
          res.content().stream()
              .filter(b -> "text".equals(b.type()))
              .map(AnthropicResponse.Block::text)
              .findFirst()
              .orElseThrow(() -> new AiUnavailableException("Réponse IA sans texte"));
      int tokens = res.usage() == null ? 0 : res.usage().inputTokens() + res.usage().outputTokens();
      return new InsightResult(text.trim(), tokens, res.model() == null ? model : res.model());
    } catch (RestClientException e) {
      // Ne pas fuiter de détail (ni la clé) ; l'IA est indisponible → 503.
      throw new AiUnavailableException("Service IA indisponible");
    }
  }

  private String userPrompt(InsightFacts f) {
    StringBuilder sb = new StringBuilder();
    sb.append("Voici les chiffres du mois (")
        .append(f.periodStart())
        .append(" → ")
        .append(f.periodEnd())
        .append(") :\n");
    sb.append("- Revenus : ").append(money(f.income())).append("\n");
    sb.append("- Dépenses : ").append(money(f.expense())).append("\n");
    sb.append("- Solde net : ").append(money(f.net())).append("\n");
    sb.append("- Nombre de transactions : ").append(f.transactionCount()).append("\n");
    if (f.topCategories() != null && !f.topCategories().isEmpty()) {
      sb.append("- Principales dépenses par catégorie :\n");
      for (CategoryShare c : f.topCategories()) {
        sb.append("  • ").append(c.name()).append(" : ").append(money(c.amount())).append("\n");
      }
    }
    sb.append(
        "\nRédige le résumé en français en t'appuyant UNIQUEMENT sur ces chiffres, sans en inventer"
            + " d'autres.");
    return sb.toString();
  }

  private String qaUserPrompt(AskContext ctx, String question) {
    StringBuilder sb = new StringBuilder();
    sb.append("Contexte financier de l'utilisateur (chiffres fiables, calculés) :\n");
    InsightFacts m = ctx.month();
    sb.append("Mois courant (")
        .append(m.periodStart())
        .append(" → ")
        .append(m.periodEnd())
        .append(") :\n");
    sb.append("- Revenus : ").append(money(m.income())).append("\n");
    sb.append("- Dépenses : ").append(money(m.expense())).append("\n");
    sb.append("- Solde net : ").append(money(m.net())).append("\n");
    sb.append("- Nombre de transactions : ").append(m.transactionCount()).append("\n");
    if (m.topCategories() != null && !m.topCategories().isEmpty()) {
      sb.append("- Principales dépenses par catégorie :\n");
      for (CategoryShare c : m.topCategories()) {
        sb.append("  • ").append(c.name()).append(" : ").append(money(c.amount())).append("\n");
      }
    }
    sb.append("Solde total des comptes : ").append(money(ctx.totalBalance())).append("\n");
    if (ctx.goals() != null && !ctx.goals().isEmpty()) {
      sb.append("Objectifs d'épargne (épargné / cible) :\n");
      for (AskContext.GoalBrief g : ctx.goals()) {
        sb.append("  • ")
            .append(g.name())
            .append(" : ")
            .append(money(g.current()))
            .append(" / ")
            .append(money(g.target()))
            .append("\n");
      }
    }
    if (ctx.recentMonths() != null && !ctx.recentMonths().isEmpty()) {
      sb.append("Dépenses totales des derniers mois :\n");
      for (AskContext.MonthlyExpense e : ctx.recentMonths()) {
        sb.append("  • ").append(e.period()).append(" : ").append(money(e.total())).append("\n");
      }
    }
    sb.append("\nQuestion de l'utilisateur : ").append(question).append("\n");
    sb.append(
        "Réponds en français en t'appuyant UNIQUEMENT sur ces chiffres. Si l'information"
            + " nécessaire n'est pas présente dans ce contexte, dis-le honnêtement plutôt que"
            + " d'inventer.");
    return sb.toString();
  }

  private static String money(BigDecimal v) {
    return (v == null ? BigDecimal.ZERO : v).setScale(2, java.math.RoundingMode.HALF_UP) + " €";
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record AnthropicResponse(List<Block> content, Usage usage, String model) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Block(String type, String text) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Usage(
        @JsonProperty("input_tokens") int inputTokens,
        @JsonProperty("output_tokens") int outputTokens) {}
  }
}
