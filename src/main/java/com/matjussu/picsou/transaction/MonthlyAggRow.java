package com.matjussu.picsou.transaction;

import java.math.BigDecimal;

/** Projection Spring Data : total par mois 'YYYY-MM' (dashboard série mensuelle). */
public interface MonthlyAggRow {
  String getPeriod();

  BigDecimal getTotal();
}
