package com.matjussu.picsou.prediction.dto;

import com.matjussu.picsou.prediction.Anomaly;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Prédiction de fin de mois orientée user courant.
 *
 * @param forecastDate fin du mois courant
 * @param predictedBalance solde net projeté (blend run-rate / historique)
 * @param lowConfidence true si pas assez d'historique (run-rate seul)
 * @param anomalies catégories en dépassement significatif
 */
public record PredictionResponse(
    LocalDate forecastDate,
    BigDecimal predictedBalance,
    boolean lowConfidence,
    List<Anomaly> anomalies) {}
