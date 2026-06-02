package com.matjussu.picsou.coloc;

import com.matjussu.picsou.coloc.dto.AddSharedExpenseRequest;
import com.matjussu.picsou.coloc.dto.SharedExpenseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/coloc/groups/{groupId}/expenses")
@RequiredArgsConstructor
@Tag(name = "Coloc", description = "Colocation : groupes, membres et dépenses partagées")
public class SharedExpenseController {

  private final SharedExpenseService service;

  @GetMapping
  @Operation(
      summary = "Lister les dépenses partagées d'un groupe",
      description = "Ordonnées par date de transaction (desc). yourShare = part du user courant.")
  @ApiResponse(responseCode = "200", description = "Dépenses partagées du groupe")
  @ApiResponse(responseCode = "404", description = "Groupe inconnu ou utilisateur non membre")
  public List<SharedExpenseResponse> list(
      @AuthenticationPrincipal UUID userId, @PathVariable UUID groupId) {
    return service.list(userId, groupId);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Ajouter une dépense partagée (split egal ou custom)",
      description =
          "Crée la transaction du payeur + la dépense partagée + les parts. En equal le payeur"
              + " absorbe le reste de centimes ; en custom la somme des parts doit valoir le total.")
  @ApiResponse(responseCode = "201", description = "Dépense partagée créée")
  @ApiResponse(responseCode = "400", description = "Payeur/participant non membre ou doublon")
  @ApiResponse(responseCode = "404", description = "Groupe inconnu ou utilisateur non membre")
  @ApiResponse(responseCode = "422", description = "Split custom : somme des parts ≠ total")
  public SharedExpenseResponse add(
      @AuthenticationPrincipal UUID userId,
      @PathVariable UUID groupId,
      @Valid @RequestBody AddSharedExpenseRequest req) {
    return service.addExpense(userId, groupId, req);
  }
}
