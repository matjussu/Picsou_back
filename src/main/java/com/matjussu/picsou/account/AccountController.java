package com.matjussu.picsou.account;

import com.matjussu.picsou.account.dto.AccountResponse;
import com.matjussu.picsou.account.dto.CreateAccountRequest;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "Comptes de l'utilisateur (cash, coloc, banque)")
public class AccountController {

  private final AccountService service;

  @GetMapping
  @Operation(summary = "Lister les comptes de l'utilisateur courant")
  @ApiResponse(responseCode = "200", description = "Liste des comptes")
  public List<AccountResponse> list(@AuthenticationPrincipal UUID userId) {
    return service.list(userId);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Créer un compte manuel (cash/coloc)")
  @ApiResponse(responseCode = "201", description = "Compte créé")
  public AccountResponse create(
      @AuthenticationPrincipal UUID userId, @Valid @RequestBody CreateAccountRequest req) {
    return service.create(userId, req);
  }
}
