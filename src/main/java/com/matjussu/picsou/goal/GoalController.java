package com.matjussu.picsou.goal;

import com.matjussu.picsou.goal.dto.AddContributionRequest;
import com.matjussu.picsou.goal.dto.ContributionResponse;
import com.matjussu.picsou.goal.dto.CreateGoalRequest;
import com.matjussu.picsou.goal.dto.GoalDetailResponse;
import com.matjussu.picsou.goal.dto.GoalResponse;
import com.matjussu.picsou.goal.dto.UpdateGoalRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
@Tag(name = "Goals", description = "Objectifs d'épargne de l'utilisateur + contributions")
public class GoalController {

  private final GoalService service;

  @GetMapping
  @Operation(summary = "Lister les objectifs (filtre status=active|completed optionnel)")
  @ApiResponse(responseCode = "200", description = "Liste des objectifs")
  public List<GoalResponse> list(
      @AuthenticationPrincipal UUID userId, @RequestParam(required = false) String status) {
    return service.list(userId, status);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Créer un objectif d'épargne")
  @ApiResponse(responseCode = "201", description = "Objectif créé")
  public GoalResponse create(
      @AuthenticationPrincipal UUID userId, @Valid @RequestBody CreateGoalRequest req) {
    return service.create(userId, req);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Détail d'un objectif (avec ses contributions)")
  @ApiResponse(responseCode = "200", description = "Objectif trouvé")
  @ApiResponse(responseCode = "404", description = "Objectif inconnu ou d'un autre utilisateur")
  public GoalDetailResponse get(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
    return service.get(userId, id);
  }

  @PatchMapping("/{id}")
  @Operation(summary = "Modifier un objectif")
  @ApiResponse(responseCode = "200", description = "Objectif modifié")
  public GoalResponse update(
      @AuthenticationPrincipal UUID userId,
      @PathVariable UUID id,
      @RequestBody UpdateGoalRequest req) {
    return service.update(userId, id, req);
  }

  @PostMapping("/{id}/contributions")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Ajouter une contribution à un objectif",
      description = "Met à jour le montant épargné ; marque l'objectif atteint si la cible est franchie.")
  @ApiResponse(responseCode = "201", description = "Contribution ajoutée")
  public ContributionResponse addContribution(
      @AuthenticationPrincipal UUID userId,
      @PathVariable UUID id,
      @Valid @RequestBody AddContributionRequest req) {
    return service.addContribution(userId, id, req);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Supprimer un objectif (et ses contributions)")
  @ApiResponse(responseCode = "204", description = "Objectif supprimé")
  public void delete(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
    service.delete(userId, id);
  }
}
