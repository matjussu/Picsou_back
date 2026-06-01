package com.matjussu.picsou.goal.dto;

import com.matjussu.picsou.goal.GoalTemplate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateGoalRequest(
    @NotBlank String name,
    @NotNull @Positive BigDecimal targetAmount,
    LocalDate deadline,
    GoalTemplate template) {}
