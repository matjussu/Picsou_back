package com.matjussu.picsou.dashboard.dto;

import java.math.BigDecimal;

/** Une part de la répartition des dépenses par catégorie (mois courant). */
public record CategorySlice(String category, BigDecimal total) {}
