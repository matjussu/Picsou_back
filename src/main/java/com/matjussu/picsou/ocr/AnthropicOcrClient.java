package com.matjussu.picsou.ocr;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.matjussu.picsou.ai.AiUnavailableException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Impl prod de {@link OcrClient} via la vision Claude + <b>tool use</b> (parsing structuré, zéro
 * regex). L'image est encodée base64 en mémoire pour l'appel puis jetée — jamais persistée
 * (privacy, garde-fou #4). Clé absente/erreur → {@link AiUnavailableException} (503).
 */
@Component
public class AnthropicOcrClient implements OcrClient {

  private static final String ANTHROPIC_VERSION = "2023-06-01";
  private static final int MAX_TOKENS = 512;
  private static final String TOOL = "extract_receipt";
  private static final Map<String, Object> TOOL_SCHEMA =
      Map.of(
          "name",
          TOOL,
          "description",
          "Enregistre les informations extraites d'un reçu d'achat.",
          "input_schema",
          Map.of(
              "type",
              "object",
              "properties",
              Map.of(
                  "total",
                  Map.of("type", "number", "description", "Montant total payé"),
                  "merchant",
                  Map.of("type", "string", "description", "Nom du marchand / enseigne"),
                  "date",
                  Map.of("type", "string", "description", "Date du reçu au format YYYY-MM-DD"))));

  private final String apiKey;
  private final String model;
  private final RestClient rest;

  public AnthropicOcrClient(
      @Value("${app.ai.anthropic-api-key:}") String apiKey,
      @Value("${app.ai.model:claude-sonnet-4-6}") String model,
      @Value("${app.ai.base-url:https://api.anthropic.com}") String baseUrl) {
    this.apiKey = apiKey;
    this.model = model;
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(10_000);
    factory.setReadTimeout(60_000); // la vision peut être lente
    this.rest = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
  }

  @Override
  public ReceiptExtraction extractReceipt(byte[] image, String mediaType) {
    if (!StringUtils.hasText(apiKey)) {
      throw new AiUnavailableException("IA non configurée (clé Anthropic absente)");
    }
    String base64 = Base64.getEncoder().encodeToString(image);
    Map<String, Object> body =
        Map.of(
            "model",
            model,
            "max_tokens",
            MAX_TOKENS,
            "tools",
            List.of(TOOL_SCHEMA),
            "tool_choice",
            Map.of("type", "tool", "name", TOOL),
            "messages",
            List.of(
                Map.of(
                    "role",
                    "user",
                    "content",
                    List.of(
                        Map.of(
                            "type",
                            "image",
                            "source",
                            Map.of("type", "base64", "media_type", mediaType, "data", base64)),
                        Map.of(
                            "type",
                            "text",
                            "text",
                            "Extrais le total, le marchand et la date de ce reçu via l'outil"
                                + " extract_receipt.")))));
    try {
      AnthropicResponse res =
          rest.post()
              .uri("/v1/messages")
              .header("x-api-key", apiKey)
              .header("anthropic-version", ANTHROPIC_VERSION)
              .body(body)
              .retrieve()
              .body(AnthropicResponse.class);
      Map<String, Object> input = toolInput(res);
      return new ReceiptExtraction(
          parseAmount(input.get("total")),
          asString(input.get("merchant")),
          parseDate(input.get("date")));
    } catch (RestClientException e) {
      throw new AiUnavailableException("Service IA indisponible");
    }
  }

  private Map<String, Object> toolInput(AnthropicResponse res) {
    if (res == null || res.content() == null) {
      return Map.of();
    }
    return res.content().stream()
        .filter(b -> "tool_use".equals(b.type()) && b.input() != null)
        .map(AnthropicResponse.Block::input)
        .findFirst()
        .orElse(Map.of());
  }

  private BigDecimal parseAmount(Object value) {
    if (value == null) {
      return null;
    }
    try {
      return new BigDecimal(String.valueOf(value)).setScale(2, java.math.RoundingMode.HALF_UP);
    } catch (NumberFormatException e) {
      return null; // extraction partielle tolérée
    }
  }

  private LocalDate parseDate(Object value) {
    if (value == null) {
      return null;
    }
    try {
      return LocalDate.parse(String.valueOf(value));
    } catch (RuntimeException e) {
      return null;
    }
  }

  private String asString(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record AnthropicResponse(List<Block> content) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Block(String type, String name, Map<String, Object> input) {}
  }
}
