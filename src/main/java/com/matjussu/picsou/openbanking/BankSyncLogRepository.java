package com.matjussu.picsou.openbanking;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankSyncLogRepository extends JpaRepository<BankSyncLog, UUID> {

  /** Dernière synchro d'une connexion (alimente le badge « dernière synchro »). */
  Optional<BankSyncLog> findFirstByBankConnectionIdOrderByStartedAtDesc(UUID bankConnectionId);
}
