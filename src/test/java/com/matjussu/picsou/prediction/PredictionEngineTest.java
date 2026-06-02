package com.matjussu.picsou.prediction;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests purs (sans Spring) du moteur de prédiction. */
class PredictionEngineTest {

  private static BigDecimal bd(String v) {
    return new BigDecimal(v);
  }

  @Test
  void blends_runrate_and_history_weighted_by_month_progress() {
    // netSoFar 500 sur 10/30 jours → run-rate 1500 ; historique moyen 300.
    // weight=1/3 → 1500*1/3 + 300*2/3 = 500 + 200 = 700.
    PredictionEngine.Result r =
        PredictionEngine.predict(
            bd("500"), 10, 30, List.of(bd("300"), bd("300"), bd("300")), Map.of(), Map.of());

    assertThat(r.predictedBalance()).isEqualByComparingTo("700.00");
    assertThat(r.lowConfidence()).isFalse();
  }

  @Test
  void without_history_uses_runrate_only_and_flags_low_confidence() {
    PredictionEngine.Result r =
        PredictionEngine.predict(bd("400"), 10, 30, List.of(), Map.of(), Map.of());

    assertThat(r.predictedBalance()).isEqualByComparingTo("1200.00"); // 400/10*30
    assertThat(r.lowConfidence()).isTrue();
    assertThat(r.anomalies()).isEmpty();
  }

  @Test
  void day_zero_falls_back_to_historical_average() {
    PredictionEngine.Result r =
        PredictionEngine.predict(bd("0"), 0, 30, List.of(bd("300"), bd("300")), Map.of(), Map.of());

    assertThat(r.predictedBalance()).isEqualByComparingTo("300.00"); // anti /0 → moyenne
  }

  @Test
  void detects_category_anomaly_above_threshold() {
    PredictionEngine.Result r =
        PredictionEngine.predict(
            bd("100"),
            15,
            30,
            List.of(bd("100")),
            Map.of("Restaurant", bd("200")),
            Map.of("Restaurant", bd("100")));

    assertThat(r.anomalies()).hasSize(1);
    assertThat(r.anomalies().get(0).category()).isEqualTo("Restaurant");
    assertThat(r.anomalies().get(0).deltaPct()).isEqualTo(100); // +100 % vs moyenne
  }

  @Test
  void ignores_small_or_baseline_less_categories() {
    PredictionEngine.Result r =
        PredictionEngine.predict(
            bd("100"),
            15,
            30,
            List.of(bd("100")),
            Map.of(
                "Cafe", bd("20"), // +100% mais delta 10€ < 15€ → ignoré
                "Loyer", bd("110"), // +10% < 30% → ignoré
                "Nouvelle", bd("500")), // pas de baseline → ignoré
            Map.of("Cafe", bd("10"), "Loyer", bd("100")));

    assertThat(r.anomalies()).isEmpty();
  }
}
