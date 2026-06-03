package com.matjussu.picsou.ai;

import com.matjussu.picsou.ai.dto.AskRequest;
import com.matjussu.picsou.ai.dto.AskResponse;
import com.matjussu.picsou.ai.dto.InsightResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
@Tag(
    name = "Insights",
    description = "Résumé IA du mois + Q&A libre (prose FR autour de nos chiffres)")
public class InsightController {

  private final InsightService service;
  private final AskService askService;

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

  @PostMapping("/ask")
  @Operation(
      summary = "Poser une question libre à l'IA sur ses finances",
      description =
          "Le LLM répond en FR en s'appuyant UNIQUEMENT sur nos chiffres (mois courant, solde,"
              + " objectifs, tendance) ; il n'invente aucun montant et reste honnête hors"
              + " périmètre. 400 si la question est vide/trop longue, 503 si l'IA n'est pas"
              + " configurée.")
  @ApiResponse(responseCode = "200", description = "Réponse de l'IA")
  @ApiResponse(responseCode = "400", description = "Question vide ou trop longue")
  @ApiResponse(responseCode = "503", description = "IA non configurée / indisponible")
  public AskResponse ask(@AuthenticationPrincipal UUID userId, @Valid @RequestBody AskRequest req) {
    return askService.ask(userId, req.question());
  }
}
