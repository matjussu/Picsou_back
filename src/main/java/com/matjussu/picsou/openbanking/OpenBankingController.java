package com.matjussu.picsou.openbanking;

import com.matjussu.picsou.openbanking.dto.ConnectRequest;
import com.matjussu.picsou.openbanking.dto.ConnectionResponse;
import com.matjussu.picsou.openbanking.dto.InstitutionResponse;
import com.matjussu.picsou.openbanking.dto.SyncResultResponse;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/openbanking")
@RequiredArgsConstructor
@Tag(name = "Open Banking", description = "Connexion de comptes bancaires (mock, données simulées)")
public class OpenBankingController {

  private final OpenBankingService service;

  @GetMapping("/institutions")
  @Operation(summary = "Lister les banques connectables (catalogue mock)")
  @ApiResponse(responseCode = "200", description = "Catalogue des banques")
  public List<InstitutionResponse> institutions() {
    return service.institutions();
  }

  @GetMapping("/connections")
  @Operation(summary = "Lister les connexions bancaires de l'utilisateur courant")
  @ApiResponse(responseCode = "200", description = "Connexions avec statut + dernière synchro")
  public List<ConnectionResponse> connections(@AuthenticationPrincipal UUID userId) {
    return service.listConnections(userId);
  }

  @PostMapping("/connections")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Connecter une banque (OAuth simulé) + première synchro")
  @ApiResponse(responseCode = "201", description = "Banque connectée")
  @ApiResponse(responseCode = "404", description = "Banque inconnue du catalogue")
  @ApiResponse(responseCode = "409", description = "Banque déjà connectée")
  public ConnectionResponse connect(
      @AuthenticationPrincipal UUID userId, @Valid @RequestBody ConnectRequest req) {
    return service.connect(userId, req.institutionId());
  }

  @PostMapping("/connections/{id}/sync")
  @Operation(summary = "Synchroniser les transactions d'une connexion (mock)")
  @ApiResponse(responseCode = "200", description = "Synchro effectuée")
  @ApiResponse(responseCode = "404", description = "Connexion inconnue")
  @ApiResponse(responseCode = "409", description = "Connexion inactive")
  public SyncResultResponse sync(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
    return service.sync(userId, id);
  }

  @DeleteMapping("/connections/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Déconnecter une banque (passe en révoquée, historique conservé)")
  @ApiResponse(responseCode = "204", description = "Connexion révoquée")
  @ApiResponse(responseCode = "404", description = "Connexion inconnue")
  public void disconnect(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
    service.disconnect(userId, id);
  }
}
