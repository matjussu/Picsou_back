package com.matjussu.picsou.account;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {

  List<Account> findByUserId(UUID userId);

  Optional<Account> findByIdAndUserId(UUID id, UUID userId);

  /** Compte rattaché à une connexion Open Banking (external_id = id de la connexion). */
  Optional<Account> findByUserIdAndExternalId(UUID userId, String externalId);
}
