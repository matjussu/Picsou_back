package com.matjussu.picsou.goal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateGoalRequest(String name, BigDecimal targetAmount, LocalDate deadline) {}
