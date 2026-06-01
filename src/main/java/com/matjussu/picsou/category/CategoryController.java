package com.matjussu.picsou.category;

import com.matjussu.picsou.category.dto.CategoryResponse;
import com.matjussu.picsou.category.dto.CreateCategoryRequest;
import com.matjussu.picsou.category.dto.UpdateCategoryRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Tag(
    name = "Categories",
    description = "Catégories par défaut (globales) + personnalisées de l'utilisateur")
public class CategoryController {

  private final CategoryService service;

  @GetMapping
  @Operation(summary = "Lister les catégories (globales + perso de l'utilisateur)")
  @ApiResponse(responseCode = "200", description = "Liste des catégories")
  public List<CategoryResponse> list(@AuthenticationPrincipal UUID userId) {
    return service.list(userId);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Créer une catégorie personnalisée")
  @ApiResponse(responseCode = "201", description = "Catégorie créée")
  public CategoryResponse create(
      @AuthenticationPrincipal UUID userId, @Valid @RequestBody CreateCategoryRequest req) {
    return service.create(userId, req);
  }

  @PatchMapping("/{id}")
  @Operation(summary = "Modifier une catégorie personnalisée")
  @ApiResponse(responseCode = "200", description = "Catégorie modifiée")
  public CategoryResponse update(
      @AuthenticationPrincipal UUID userId,
      @PathVariable UUID id,
      @RequestBody UpdateCategoryRequest req) {
    return service.update(userId, id, req);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Supprimer une catégorie personnalisée")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Catégorie supprimée"),
    @ApiResponse(
        responseCode = "409",
        description = "Catégorie par défaut ou référencée par des transactions")
  })
  public void delete(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
    service.delete(userId, id);
  }
}
