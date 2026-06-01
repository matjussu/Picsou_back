package com.matjussu.picsou.dashboard;

import com.matjussu.picsou.dashboard.dto.CategorySlice;
import com.matjussu.picsou.dashboard.dto.DashboardSummary;
import com.matjussu.picsou.dashboard.dto.MonthlyPoint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Agrégats du tableau de bord (KPIs + données de graphes)")
public class DashboardController {

  private final DashboardService service;

  @GetMapping("/summary")
  @Operation(summary = "KPIs du mois courant (revenus, dépenses, solde, nb transactions)")
  @ApiResponse(responseCode = "200", description = "Résumé du mois")
  public DashboardSummary summary(@AuthenticationPrincipal UUID userId) {
    return service.summary(userId);
  }

  @GetMapping("/charts/monthly")
  @Operation(
      summary = "Dépenses des 12 derniers mois",
      description = "12 points continus (mois sans dépense à 0), le mois courant est marqué.")
  @ApiResponse(responseCode = "200", description = "Série mensuelle")
  public List<MonthlyPoint> monthly(@AuthenticationPrincipal UUID userId) {
    return service.monthly(userId);
  }

  @GetMapping("/charts/category-breakdown")
  @Operation(summary = "Répartition des dépenses du mois courant par catégorie (triée décroissante)")
  @ApiResponse(responseCode = "200", description = "Répartition par catégorie")
  public List<CategorySlice> categoryBreakdown(@AuthenticationPrincipal UUID userId) {
    return service.categoryBreakdown(userId);
  }
}
