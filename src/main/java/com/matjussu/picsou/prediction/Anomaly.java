package com.matjussu.picsou.prediction;

import java.math.BigDecimal;

/**
 * Anomalie de dépense d'une catégorie sur le mois courant vs sa moyenne historique. Sérialisée en
 * JSONB dans {@code predictions.anomalies}.
 *
 * @param category nom de la catégorie
 * @param current dépense du mois courant
 * @param average moyenne glissante (3 mois)
 * @param deltaPct dépassement en % (arrondi)
 */
public record Anomaly(String category, BigDecimal current, BigDecimal average, int deltaPct) {}
