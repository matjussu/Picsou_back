package com.matjussu.picsou.dashboard.dto;

import java.math.BigDecimal;

/** Un point de la série mensuelle (12 mois continus). {@code current} = mois en cours. */
public record MonthlyPoint(String period, BigDecimal total, boolean current) {}
