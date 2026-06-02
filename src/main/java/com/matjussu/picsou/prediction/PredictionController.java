package com.matjussu.picsou.prediction;

import com.matjussu.picsou.prediction.dto.PredictionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/predictions")
@RequiredArgsConstructor
@Tag(name = "Predictions", description = "Prédictions financières (solde projeté + anomalies)")
public class PredictionController {

  private final PredictionService service;

  @GetMapping("/end-of-month")
  @Operation(
      summary = "Solde projeté en fin de mois + anomalies",
      description =
          "Algorithme local (sans IA) : blend run-rate du mois courant / moyenne historique pondéré"
              + " par l'avancement du mois, + détection d'anomalies par catégorie. Toujours"
              + " disponible (ne dépend pas de la clé Anthropic).")
  @ApiResponse(responseCode = "200", description = "Prédiction calculée")
  public PredictionResponse endOfMonth(@AuthenticationPrincipal UUID userId) {
    return service.endOfMonth(userId);
  }
}
