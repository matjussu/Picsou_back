package com.matjussu.picsou.coloc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ColocMemberRepository extends JpaRepository<ColocMember, UUID> {

  List<ColocMember> findByColocGroupId(UUID colocGroupId);

  Optional<ColocMember> findByColocGroupIdAndUserId(UUID colocGroupId, UUID userId);

  boolean existsByColocGroupIdAndUserId(UUID colocGroupId, UUID userId);

  List<ColocMember> findByUserId(UUID userId);
}
