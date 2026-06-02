package com.matjussu.picsou.prediction;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Moteur de prédiction — fonction <b>pure</b> (aucune dépendance Spring/JPA), testable en
 * isolation.
 *
 * <p><b>Solde projeté fin de mois</b> = blend pondéré par l'avancement du mois entre le run-rate du
 * mois courant et la moyenne historique : en début de mois le run-rate est bruité, on s'appuie sur
 * l'historique ; en fin de mois on fait confiance au réel.
 *
 * <p><b>Anomalies</b> : par catégorie, dépense du mois courant vs moyenne glissante — signalée si
 * elle dépasse la moyenne d'au moins 30 % <b>et</b> de plus de 15 € en absolu (anti-bruit sur les
 * petites catégories).
 */
public final class PredictionEngine {

  private static final BigDecimal ANOMALY_RATIO = new BigDecimal("1.30");
  private static final BigDecimal ANOMALY_MIN_DELTA = new BigDecimal("15");
  private static final MathContext MC = MathContext.DECIMAL64;

  private PredictionEngine() {}

  /** Résultat de prédiction (solde projeté + confiance + anomalies). */
  public record Result(
      BigDecimal predictedBalance, boolean lowConfidence, List<Anomaly> anomalies) {}

  /**
   * @param netSoFar net (revenus − dépenses) du mois courant jusqu'à aujourd'hui
   * @param daysElapsed jours écoulés dans le mois (≥ 0)
   * @param daysInMonth nombre de jours du mois
   * @param historicalNets nets mensuels des mois précédents (vide = pas d'historique)
   * @param currentExpenseByCategory dépense du mois courant par catégorie
   * @param historicalAvgExpenseByCategory moyenne historique de dépense par catégorie
   */
  public static Result predict(
      BigDecimal netSoFar,
      int daysElapsed,
      int daysInMonth,
      List<BigDecimal> historicalNets,
      Map<String, BigDecimal> currentExpenseByCategory,
      Map<String, BigDecimal> historicalAvgExpenseByCategory) {
    boolean hasHistory = historicalNets != null && !historicalNets.isEmpty();
    BigDecimal historicalAvg = hasHistory ? average(historicalNets) : BigDecimal.ZERO;

    BigDecimal predicted;
    if (daysElapsed <= 0) {
      predicted = historicalAvg; // anti division par zéro (le 1er du mois) → fallback historique
    } else {
      BigDecimal runRate =
          netSoFar
              .divide(BigDecimal.valueOf(daysElapsed), MC)
              .multiply(BigDecimal.valueOf(daysInMonth));
      if (!hasHistory) {
        predicted = runRate; // pas d'historique → run-rate seul
      } else {
        BigDecimal weight =
            BigDecimal.valueOf(daysElapsed).divide(BigDecimal.valueOf(daysInMonth), MC);
        predicted =
            runRate.multiply(weight).add(historicalAvg.multiply(BigDecimal.ONE.subtract(weight)));
      }
    }

    List<Anomaly> anomalies =
        hasHistory
            ? detectAnomalies(currentExpenseByCategory, historicalAvgExpenseByCategory)
            : List.of();
    return new Result(money(predicted), !hasHistory, anomalies);
  }

  private static List<Anomaly> detectAnomalies(
      Map<String, BigDecimal> current, Map<String, BigDecimal> historicalAvg) {
    List<Anomaly> anomalies = new ArrayList<>();
    if (current == null) {
      return anomalies;
    }
    current.forEach(
        (category, spent) -> {
          BigDecimal avg = historicalAvg == null ? null : historicalAvg.get(category);
          if (avg == null || avg.signum() <= 0 || spent == null) {
            return; // pas de baseline → pas une anomalie
          }
          BigDecimal delta = spent.subtract(avg);
          if (spent.compareTo(avg.multiply(ANOMALY_RATIO)) > 0
              && delta.compareTo(ANOMALY_MIN_DELTA) > 0) {
            int deltaPct =
                delta
                    .multiply(BigDecimal.valueOf(100))
                    .divide(avg, 0, RoundingMode.HALF_UP)
                    .intValue();
            anomalies.add(new Anomaly(category, money(spent), money(avg), deltaPct));
          }
        });
    anomalies.sort(Comparator.comparingInt(Anomaly::deltaPct).reversed());
    return anomalies;
  }

  private static BigDecimal average(List<BigDecimal> values) {
    BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    return sum.divide(BigDecimal.valueOf(values.size()), MC);
  }

  private static BigDecimal money(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
  }
}
