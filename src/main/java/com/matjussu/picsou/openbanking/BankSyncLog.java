package com.matjussu.picsou.openbanking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * Trace d'une synchronisation d'une {@link BankConnection} : horodatage début/fin et nombre de
 * transactions importées. La dernière entrée alimente le badge « dernière synchro » côté UI.
 */
@Entity
@Table(name = "bank_sync_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankSyncLog {

  @Id @GeneratedValue private UUID id;

  @Column(name = "bank_connection_id", nullable = false)
  private UUID bankConnectionId;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "transactions_imported", nullable = false)
  private int transactionsImported;

  @Column(name = "error")
  private String error;

  @PrePersist
  void onCreate() {
    if (this.startedAt == null) {
      this.startedAt = Instant.now();
    }
  }
}
