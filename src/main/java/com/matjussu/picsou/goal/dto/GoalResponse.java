package com.matjussu.picsou.goal.dto;

import com.matjussu.picsou.goal.GoalTemplate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record GoalResponse(
    UUID id,
    String name,
    BigDecimal targetAmount,
    BigDecimal currentAmount,
    LocalDate deadline,
    GoalTemplate template,
    boolean completed,
    int progressPercent) {}
