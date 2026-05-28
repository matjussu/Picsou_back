package com.matjussu.picsou.auth;

import com.matjussu.picsou.auth.dto.AuthResponse;
import com.matjussu.picsou.auth.dto.LoginRequest;
import com.matjussu.picsou.auth.dto.RefreshRequest;
import com.matjussu.picsou.auth.dto.SignupRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Endpoints d'authentification : signup, login, refresh, logout")
public class AuthController {

  private final AuthService authService;

  @PostMapping("/signup")
  @Operation(summary = "Créer un compte utilisateur")
  @ApiResponse(responseCode = "201", description = "Compte créé + tokens retournés")
  @ApiResponse(responseCode = "409", description = "Email déjà utilisé")
  public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(req));
  }

  @PostMapping("/login")
  @Operation(summary = "Connexion avec email + mot de passe")
  @ApiResponse(responseCode = "200", description = "Login OK + tokens retournés")
  @ApiResponse(responseCode = "401", description = "Identifiants invalides")
  public AuthResponse login(@Valid @RequestBody LoginRequest req) {
    return authService.login(req);
  }

  @PostMapping("/refresh")
  @Operation(summary = "Rafraîchir l'access token via refresh token")
  public AuthResponse refresh(@Valid @RequestBody RefreshRequest req) {
    return authService.refresh(req);
  }

  @PostMapping("/logout")
  @Operation(summary = "Révoquer le refresh token courant")
  public void logout(@Valid @RequestBody RefreshRequest req) {
    authService.logout(req);
  }
}
