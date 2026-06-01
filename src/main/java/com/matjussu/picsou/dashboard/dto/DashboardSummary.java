package com.matjussu.picsou.dashboard.dto;

import java.math.BigDecimal;

/** KPIs du mois courant (user-scopé). */
public record DashboardSummary(
    BigDecimal income, BigDecimal expense, BigDecimal balance, int transactionCount) {}
