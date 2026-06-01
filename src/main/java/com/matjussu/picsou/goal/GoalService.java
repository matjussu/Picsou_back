package com.matjussu.picsou.goal;

import com.matjussu.picsou.goal.dto.AddContributionRequest;
import com.matjussu.picsou.goal.dto.ContributionResponse;
import com.matjussu.picsou.goal.dto.CreateGoalRequest;
import com.matjussu.picsou.goal.dto.GoalDetailResponse;
import com.matjussu.picsou.goal.dto.GoalResponse;
import com.matjussu.picsou.goal.dto.UpdateGoalRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class GoalService {

  private final GoalRepository goals;
  private final GoalContributionRepository contributions;

  public List<GoalResponse> list(UUID userId, String status) {
    List<Goal> found =
        switch (status == null ? "" : status) {
          case "active" -> goals.findByUserIdAndCompletedAtIsNull(userId);
          case "completed" -> goals.findByUserIdAndCompletedAtIsNotNull(userId);
          default -> goals.findByUserId(userId);
        };
    return found.stream().map(this::toResponse).toList();
  }

  public GoalResponse create(UUID userId, CreateGoalRequest req) {
    Goal g =
        goals.save(
            Goal.builder()
                .userId(userId)
                .name(req.name())
                .targetAmount(req.targetAmount())
                .currentAmount(BigDecimal.ZERO)
                .deadline(req.deadline())
                .template(req.template() != null ? req.template() : GoalTemplate.custom)
                .build());
    return toResponse(g);
  }

  public GoalDetailResponse get(UUID userId, UUID id) {
    Goal g = owned(userId, id);
    List<ContributionResponse> contribs =
        contributions.findByGoalIdOrderByDateDesc(id).stream()
            .map(c -> new ContributionResponse(c.getId(), c.getAmount(), c.getDate()))
            .toList();
    return new GoalDetailResponse(toResponse(g), contribs);
  }

  public GoalResponse update(UUID userId, UUID id, UpdateGoalRequest req) {
    Goal g = owned(userId, id);
    if (req.name() != null) {
      g.setName(req.name());
    }
    if (req.targetAmount() != null) {
      g.setTargetAmount(req.targetAmount());
    }
    if (req.deadline() != null) {
      g.setDeadline(req.deadline());
    }
    return toResponse(goals.save(g));
  }

  /** G4 : insert contribution + maintien current_amount + completed_at auto, en une transaction. */
  @Transactional
  public ContributionResponse addContribution(
      UUID userId, UUID goalId, AddContributionRequest req) {
    Goal goal = owned(userId, goalId);
    GoalContribution c =
        contributions.save(
            GoalContribution.builder()
                .goalId(goalId)
                .amount(req.amount())
                .date(req.date())
                .build());
    goal.setCurrentAmount(goal.getCurrentAmount().add(req.amount()));
    if (goal.getCompletedAt() == null
        && goal.getCurrentAmount().compareTo(goal.getTargetAmount()) >= 0) {
      goal.setCompletedAt(Instant.now());
    }
    goals.save(goal);
    return new ContributionResponse(c.getId(), c.getAmount(), c.getDate());
  }

  public void delete(UUID userId, UUID id) {
    // Les contributions partent en cascade (FK ON DELETE CASCADE, V1).
    goals.delete(owned(userId, id));
  }

  private Goal owned(UUID userId, UUID id) {
    return goals
        .findByIdAndUserId(id, userId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Objectif inconnu"));
  }

  private GoalResponse toResponse(Goal g) {
    return new GoalResponse(
        g.getId(),
        g.getName(),
        g.getTargetAmount(),
        g.getCurrentAmount(),
        g.getDeadline(),
        g.getTemplate(),
        g.getCompletedAt() != null,
        progressPercent(g));
  }

  private int progressPercent(Goal g) {
    if (g.getTargetAmount() == null || g.getTargetAmount().signum() <= 0) {
      return 0;
    }
    int pct =
        g.getCurrentAmount()
            .multiply(BigDecimal.valueOf(100))
            .divide(g.getTargetAmount(), 0, RoundingMode.DOWN)
            .intValue();
    return Math.max(0, Math.min(100, pct));
  }
}
