package com.matjussu.picsou.openbanking;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankConnectionRepository extends JpaRepository<BankConnection, UUID> {

  List<BankConnection> findByUserId(UUID userId);

  Optional<BankConnection> findByIdAndUserId(UUID id, UUID userId);

  /** Garde-fou anti-doublon : une banque ne peut être connectée qu'une fois en statut donné. */
  boolean existsByUserIdAndInstitutionIdAndStatus(
      UUID userId, String institutionId, ObStatus status);
}
