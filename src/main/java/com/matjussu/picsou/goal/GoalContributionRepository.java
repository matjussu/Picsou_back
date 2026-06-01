package com.matjussu.picsou.goal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalContributionRepository extends JpaRepository<GoalContribution, UUID> {

  List<GoalContribution> findByGoalIdOrderByDateDesc(UUID goalId);
}
