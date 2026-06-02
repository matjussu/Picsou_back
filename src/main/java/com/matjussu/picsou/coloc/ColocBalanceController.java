package com.matjussu.picsou.coloc;

import com.matjussu.picsou.coloc.dto.BalanceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Coloc", description = "Colocation : soldes et règlement")
public class ColocBalanceController {

  private final ColocBalanceService balanceService;
  private final SharedExpenseService expenseService;

  @GetMapping("/api/coloc/groups/{groupId}/balances")
  @Operation(
      summary = "Bilan du groupe (soldes + virements simplifiés)",
      description =
          "Soldes nets par membre sur les parts non réglées + virements simplifiés (greedy ;"
              + " minimum exact NP-difficile). Orienté user courant (yourNet).")
  @ApiResponse(responseCode = "200", description = "Bilan du groupe")
  @ApiResponse(responseCode = "404", description = "Groupe inconnu ou utilisateur non membre")
  public BalanceResponse balances(
      @AuthenticationPrincipal UUID userId, @PathVariable UUID groupId) {
    return balanceService.balances(userId, groupId);
  }

  @PostMapping("/api/coloc/expenses/{expenseId}/settle")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Marquer une dépense partagée comme réglée",
      description = "Marque settled toutes les parts de cette dépense.")
  @ApiResponse(responseCode = "204", description = "Dépense réglée")
  @ApiResponse(responseCode = "404", description = "Dépense inconnue ou utilisateur non membre")
  public void settleExpense(@AuthenticationPrincipal UUID userId, @PathVariable UUID expenseId) {
    expenseService.settleExpense(userId, expenseId);
  }

  @PostMapping("/api/coloc/groups/{groupId}/settle-all")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Tout marquer réglé pour le groupe",
      description =
          "Marque settled toutes les parts non réglées du groupe (action après virements IRL).")
  @ApiResponse(responseCode = "204", description = "Groupe soldé")
  @ApiResponse(responseCode = "404", description = "Groupe inconnu ou utilisateur non membre")
  public void settleAll(@AuthenticationPrincipal UUID userId, @PathVariable UUID groupId) {
    expenseService.settleAll(userId, groupId);
  }
}
