package com.matjussu.picsou.coloc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.matjussu.picsou.coloc.DebtSimplifier.Transfer;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Tests purs (sans Spring) de l'algorithme de simplification des dettes. */
class DebtSimplifierTest {

  private static final UUID A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
  private static final UUID C = UUID.fromString("00000000-0000-0000-0000-0000000000c3");

  private static BigDecimal bd(String v) {
    return new BigDecimal(v);
  }

  private static BigDecimal totalMoved(List<Transfer> transfers) {
    return transfers.stream().map(Transfer::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  @Test
  void two_transfers_instead_of_three() {
    // A doit 18.50, C doit 4.50, B est créancier de 23.00 (somme = 0).
    Map<UUID, BigDecimal> balances = Map.of(A, bd("-18.50"), B, bd("23.00"), C, bd("-4.50"));

    List<Transfer> transfers = DebtSimplifier.simplify(balances);

    assertThat(transfers).hasSize(2); // greedy quasi-optimal
    assertThat(totalMoved(transfers)).isEqualByComparingTo("23.00");
    // Les deux virements vont vers le créancier B, depuis A puis C.
    assertThat(transfers).allSatisfy(t -> assertThat(t.to()).isEqualTo(B));
    assertThat(transfers.stream().map(Transfer::from)).containsExactlyInAnyOrder(A, C);
  }

  @Test
  void all_zero_returns_empty() {
    assertThat(DebtSimplifier.simplify(Map.of(A, BigDecimal.ZERO, B, BigDecimal.ZERO))).isEmpty();
  }

  @Test
  void single_member_returns_empty() {
    assertThat(DebtSimplifier.simplify(Map.of(A, BigDecimal.ZERO))).isEmpty();
  }

  @Test
  void member_with_zero_balance_is_ignored() {
    // C est neutralisé (solde 0) → un seul virement B->A.
    Map<UUID, BigDecimal> balances = Map.of(A, bd("10.00"), B, bd("-10.00"), C, BigDecimal.ZERO);

    List<Transfer> transfers = DebtSimplifier.simplify(balances);

    assertThat(transfers).hasSize(1);
    assertThat(transfers.get(0).from()).isEqualTo(B);
    assertThat(transfers.get(0).to()).isEqualTo(A);
    assertThat(transfers.get(0).amount()).isEqualByComparingTo("10.00");
  }

  @Test
  void cent_drift_within_tolerance_is_accepted() {
    // Dérive d'un centime (rounding réel) tolérée — ne lève pas.
    Map<UUID, BigDecimal> balances = Map.of(A, bd("3.34"), B, bd("-3.33"));
    assertThat(DebtSimplifier.simplify(balances)).hasSize(1);
  }

  @Test
  void grossly_unbalanced_sum_throws() {
    Map<UUID, BigDecimal> balances = Map.of(A, bd("5.00"), B, bd("-1.00"));
    assertThatThrownBy(() -> DebtSimplifier.simplify(balances))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("zéro");
  }

  @Test
  void chained_debts_simplify_to_two() {
    // A doit 10 à B, B doit 10 à C → nets A:-10, B:0, C:+10 → 1 seul virement A->C.
    Map<UUID, BigDecimal> net = new HashMap<>();
    net.put(A, bd("-10.00"));
    net.put(B, BigDecimal.ZERO);
    net.put(C, bd("10.00"));

    List<Transfer> transfers = DebtSimplifier.simplify(net);

    assertThat(transfers).hasSize(1);
    assertThat(transfers.get(0).from()).isEqualTo(A);
    assertThat(transfers.get(0).to()).isEqualTo(C);
    assertThat(transfers.get(0).amount()).isEqualByComparingTo("10.00");
  }
}
