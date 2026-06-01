package com.matjussu.picsou.transaction;

import com.matjussu.picsou.transaction.dto.CreateTransactionRequest;
import com.matjussu.picsou.transaction.dto.TransactionFilter;
import com.matjussu.picsou.transaction.dto.TransactionResponse;
import com.matjussu.picsou.transaction.dto.UpdateTransactionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Tag(
    name = "Transactions",
    description =
        "Cœur métier — CRUD + filtres backend (date, catégorie, compte, montant, type, recherche"
            + " texte)")
public class TransactionController {

  private final TransactionService service;

  @GetMapping
  @Operation(
      summary = "Lister/filtrer les transactions de l'utilisateur",
      description =
          "Tous les filtres sont appliqués côté backend via JPA Specifications. Pagination via"
              + " page/size/sort.")
  @ApiResponse(responseCode = "200", description = "Page de transactions filtrée")
  public List<TransactionResponse> search(
      @AuthenticationPrincipal UUID userId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) UUID categoryId,
      @RequestParam(required = false) UUID accountId,
      @RequestParam(required = false) BigDecimal minAmount,
      @RequestParam(required = false) BigDecimal maxAmount,
      @RequestParam(required = false) TransactionType type,
      @RequestParam(required = false) String q,
      Pageable pageable) {
    return service.search(
        userId,
        new TransactionFilter(from, to, categoryId, accountId, minAmount, maxAmount, type, q),
        pageable);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Créer une transaction manuelle")
  @ApiResponse(responseCode = "201", description = "Transaction créée")
  public TransactionResponse create(
      @AuthenticationPrincipal UUID userId, @Valid @RequestBody CreateTransactionRequest req) {
    return service.create(userId, req);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Détail d'une transaction")
  @ApiResponse(responseCode = "200", description = "Transaction trouvée")
  @ApiResponse(
      responseCode = "404",
      description = "Transaction inconnue ou appartenant à un autre utilisateur")
  public TransactionResponse get(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
    return service.get(userId, id);
  }

  @PatchMapping("/{id}")
  @Operation(summary = "Modifier une transaction")
  @ApiResponse(responseCode = "200", description = "Transaction modifiée")
  public TransactionResponse update(
      @AuthenticationPrincipal UUID userId,
      @PathVariable UUID id,
      @RequestBody UpdateTransactionRequest req) {
    return service.update(userId, id, req);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Supprimer une transaction")
  @ApiResponse(responseCode = "204", description = "Transaction supprimée")
  public void delete(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
    service.delete(userId, id);
  }
}
