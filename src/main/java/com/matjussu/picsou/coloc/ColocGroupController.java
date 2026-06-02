package com.matjussu.picsou.coloc;

import com.matjussu.picsou.coloc.dto.AddMemberRequest;
import com.matjussu.picsou.coloc.dto.CreateGroupRequest;
import com.matjussu.picsou.coloc.dto.GroupDetailResponse;
import com.matjussu.picsou.coloc.dto.GroupResponse;
import com.matjussu.picsou.coloc.dto.MemberResponse;
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
@RequestMapping("/api/coloc/groups")
@RequiredArgsConstructor
@Tag(name = "Coloc", description = "Colocation : groupes et membres")
public class ColocGroupController {

  private final ColocGroupService service;

  @GetMapping
  @Operation(summary = "Lister les groupes de coloc dont je suis membre")
  @ApiResponse(responseCode = "200", description = "Mes groupes")
  public List<GroupResponse> list(@AuthenticationPrincipal UUID userId) {
    return service.listMyGroups(userId);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Créer un groupe de coloc",
      description = "Le créateur devient automatiquement membre admin du groupe.")
  @ApiResponse(responseCode = "201", description = "Groupe créé")
  public GroupResponse create(
      @AuthenticationPrincipal UUID userId, @Valid @RequestBody CreateGroupRequest req) {
    return service.createGroup(userId, req);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Détail d'un groupe (membres + rôles)")
  @ApiResponse(responseCode = "200", description = "Groupe trouvé")
  @ApiResponse(responseCode = "404", description = "Groupe inconnu ou utilisateur non membre")
  public GroupDetailResponse get(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
    return service.getDetail(userId, id);
  }

  @PostMapping("/{id}/members")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Ajouter un membre au groupe (par email)",
      description = "Réservé à l'admin. Le user doit déjà exister. Pas de doublon de membre.")
  @ApiResponse(responseCode = "201", description = "Membre ajouté")
  @ApiResponse(responseCode = "403", description = "Action réservée à l'admin du groupe")
  @ApiResponse(responseCode = "404", description = "Groupe inconnu / non membre, ou email inconnu")
  @ApiResponse(responseCode = "409", description = "Utilisateur déjà membre du groupe")
  public MemberResponse addMember(
      @AuthenticationPrincipal UUID userId,
      @PathVariable UUID id,
      @Valid @RequestBody AddMemberRequest req) {
    return service.addMember(userId, id, req);
  }
}
