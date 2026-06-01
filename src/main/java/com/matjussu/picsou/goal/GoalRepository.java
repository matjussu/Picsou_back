package com.matjussu.picsou.goal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalRepository extends JpaRepository<Goal, UUID> {

  List<Goal> findByUserId(UUID userId);

  List<Goal> findByUserIdAndCompletedAtIsNull(UUID userId);

  List<Goal> findByUserIdAndCompletedAtIsNotNull(UUID userId);

  Optional<Goal> findByIdAndUserId(UUID id, UUID userId);
}
