package com.matjussu.picsou.goal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ContributionResponse(UUID id, BigDecimal amount, LocalDate date) {}
