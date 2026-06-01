package com.matjussu.picsou.goal.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public record AddContributionRequest(
    @NotNull @Positive BigDecimal amount, @NotNull LocalDate date) {}
