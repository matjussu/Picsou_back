package com.matjussu.picsou.ai;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiInsightRepository extends JpaRepository<AiInsight, UUID> {

  /** Dernier insight pour la période (le service vérifie ensuite la fraîcheur 24h). */
  Optional<AiInsight> findFirstByUserIdAndPeriodStartAndPeriodEndOrderByCreatedAtDesc(
      UUID userId, LocalDate periodStart, LocalDate periodEnd);
}
