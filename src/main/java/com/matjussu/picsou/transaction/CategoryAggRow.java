package com.matjussu.picsou.transaction;

import java.math.BigDecimal;
import java.util.UUID;

/** Projection Spring Data : total dépensé par catégorie (dashboard category-breakdown). */
public interface CategoryAggRow {
  UUID getCategoryId();

  BigDecimal getTotal();
}
