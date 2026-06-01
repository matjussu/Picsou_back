package com.matjussu.picsou.auth;

import com.matjussu.picsou.account.Account;
import com.matjussu.picsou.account.AccountRepository;
import com.matjussu.picsou.account.AccountType;
import com.matjussu.picsou.auth.dto.AuthResponse;
import com.matjussu.picsou.auth.dto.LoginRequest;
import com.matjussu.picsou.auth.dto.RefreshRequest;
import com.matjussu.picsou.auth.dto.SignupRequest;
import com.matjussu.picsou.auth.jwt.JwtTokenProvider;
import com.matjussu.picsou.user.RefreshToken;
import com.matjussu.picsou.user.RefreshTokenRepository;
import com.matjussu.picsou.user.User;
import com.matjussu.picsou.user.UserRepository;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserRepository users;
  private final RefreshTokenRepository refreshTokens;
  private final AccountRepository accounts;
  private final PasswordEncoder encoder;
  private final JwtTokenProvider tokenProvider;

  @Value("${app.jwt.refresh-expiration-ms}")
  private long refreshExpirationMs;

  @Transactional
  public AuthResponse signup(SignupRequest req) {
    if (users.existsByEmail(req.email())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Email déjà utilisé");
    }
    User user =
        User.builder()
            .email(req.email())
            .passwordHash(encoder.encode(req.password()))
            .firstName(req.firstName())
            .build();
    users.save(user);
    // Tout utilisateur démarre avec un compte par défaut → toute transaction a un account_id
    // non-null. user + compte créés dans la même transaction (atomicité via @Transactional).
    accounts.save(
        Account.builder()
            .userId(user.getId())
            .name("Compte courant")
            .type(AccountType.cash)
            .build());
    return generateTokens(user);
  }

  public AuthResponse login(LoginRequest req) {
    User user =
        users
            .findByEmail(req.email())
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identifiants invalides"));
    if (!encoder.matches(req.password(), user.getPasswordHash())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identifiants invalides");
    }
    return generateTokens(user);
  }

  public AuthResponse refresh(RefreshRequest req) {
    String hash = sha256(req.refreshToken());
    RefreshToken stored =
        refreshTokens
            .findByTokenHash(hash)
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token inconnu"));
    if (stored.getRevokedAt() != null || stored.getExpiresAt().isBefore(Instant.now())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expiré ou révoqué");
    }
    User user =
        users
            .findById(stored.getUserId())
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User inconnu"));
    return generateTokens(user);
  }

  public void logout(RefreshRequest req) {
    String hash = sha256(req.refreshToken());
    refreshTokens
        .findByTokenHash(hash)
        .ifPresent(
            t -> {
              t.setRevokedAt(Instant.now());
              refreshTokens.save(t);
            });
  }

  private AuthResponse generateTokens(User user) {
    String access = tokenProvider.generateAccessToken(user.getId());
    String refresh = tokenProvider.generateRefreshToken(user.getId());
    refreshTokens.save(
        RefreshToken.builder()
            .userId(user.getId())
            .tokenHash(sha256(refresh))
            .expiresAt(Instant.now().plusMillis(refreshExpirationMs))
            .build());
    return new AuthResponse(access, refresh, user.getFirstName());
  }

  private String sha256(String input) {
    try {
      var md = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(md.digest(input.getBytes()));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
