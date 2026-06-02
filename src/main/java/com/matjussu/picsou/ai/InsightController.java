package com.matjussu.picsou.ai;

import com.matjussu.picsou.ai.dto.InsightResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
@Tag(name = "Insights", description = "Résumé IA du mois (prose FR autour de nos chiffres)")
public class InsightController {

  private final InsightService service;

  @PostMapping("/monthly")
  @Operation(
      summary = "Générer (ou réutiliser) le résumé IA du mois",
      description =
          "Les CHIFFRES sont calculés par le backend ; le LLM ne rédige que la prose FR (n'invente"
              + " aucun montant). Cache 24h. 503 si la clé Anthropic n'est pas configurée.")
  @ApiResponse(responseCode = "200", description = "Résumé du mois (frais ou mis en cache)")
  @ApiResponse(responseCode = "503", description = "IA non configurée / indisponible")
  public InsightResponse monthly(@AuthenticationPrincipal UUID userId) {
    return service.monthly(userId);
  }
}
