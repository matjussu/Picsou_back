package com.matjussu.picsou.openbanking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

/**
 * Connexion d'un utilisateur à une banque (mock Open Banking).
 *
 * <p>En mock : {@code provider} = agrégateur factice ({@link ObProvider#bridge}), {@code
 * accessTokenEncrypted} = sentinelle {@code "MOCK"} (aucun secret réel), {@code institutionId} =
 * slug du catalogue {@link BankCatalog} qui porte l'identité de la banque (nom, logo, couleur).
 */
@Entity
@Table(name = "bank_connections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankConnection {

  @Id @GeneratedValue private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  // G1: native PG enum `ob_provider` — needs PostgreSQLEnumJdbcType, else Hibernate binds varchar.
  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(name = "provider", nullable = false)
  private ObProvider provider;

  /** Slug de la banque dans le catalogue statique (ex. {@code "bnp-paribas"}). */
  @Column(name = "institution_id")
  private String institutionId;

  @Column(name = "provider_user_id")
  private String providerUserId;

  @Column(name = "access_token_encrypted", nullable = false)
  private String accessTokenEncrypted;

  @Column(name = "refresh_token_encrypted")
  private String refreshTokenEncrypted;

  @Column(name = "expires_at")
  private Instant expiresAt;

  // G1: native PG enum `ob_status`.
  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(name = "status", nullable = false)
  private ObStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @PrePersist
  void onCreate() {
    this.createdAt = Instant.now();
    if (this.status == null) {
      this.status = ObStatus.active;
    }
  }
}
