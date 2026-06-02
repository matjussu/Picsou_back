package com.matjussu.picsou.coloc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;

/**
 * Simplification des dettes d'un groupe : transforme un jeu de soldes nets par membre en une liste
 * de virements à effectuer, en minimisant (heuristiquement) leur nombre.
 *
 * <p><b>Heuristique greedy</b> max-créancier ↔ max-débiteur : à chaque étape, le plus gros débiteur
 * rembourse le plus gros créancier. Produit au plus {@code n-1} virements.
 *
 * <p><b>Limite (à mentionner au jury) :</b> le minimum <i>exact</i> du nombre de virements est un
 * problème <b>NP-difficile</b> (réduction au partitionnement / subset-sum). Ce greedy est
 * l'heuristique standard : quasi-optimale, déterministe et lisible — pas une garantie d'optimalité
 * absolue.
 *
 * <p>Fonction <b>pure</b> (aucune dépendance Spring/JPA) → testable en isolation.
 */
public final class DebtSimplifier {

  /** Un virement à effectuer : {@code from} doit {@code amount} à {@code to}. */
  public record Transfer(UUID from, UUID to, BigDecimal amount) {}

  /** Tolérance de centime sur la somme des soldes (doit valoir ~0). */
  private static final BigDecimal SUM_TOLERANCE = new BigDecimal("0.01");

  /** En dessous, un reste est considéré nul (granularité réelle = 0.01). */
  private static final BigDecimal EPSILON = new BigDecimal("0.005");

  private DebtSimplifier() {}

  /**
   * @param balances solde net par membre : {@code > 0} créancier (on lui doit), {@code < 0}
   *     débiteur (il doit). La somme doit valoir ~0 (cohérence comptable).
   * @return la liste des virements (débiteur → créancier) ; vide si tout le monde est à zéro.
   */
  public static List<Transfer> simplify(Map<UUID, BigDecimal> balances) {
    BigDecimal sum = balances.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    if (sum.abs().compareTo(SUM_TOLERANCE) > 0) {
      throw new IllegalStateException("Les soldes ne s'équilibrent pas à zéro : " + sum);
    }

    // Max-heaps : créanciers par montant dû desc ; débiteurs par montant à payer (abs) desc.
    PriorityQueue<Entry> creditors = new PriorityQueue<>((x, y) -> y.amount.compareTo(x.amount));
    PriorityQueue<Entry> debtors = new PriorityQueue<>((x, y) -> y.amount.compareTo(x.amount));
    balances.forEach(
        (user, value) -> {
          int sign = value.compareTo(BigDecimal.ZERO);
          if (sign > 0) {
            creditors.add(new Entry(user, value));
          } else if (sign < 0) {
            debtors.add(new Entry(user, value.negate()));
          }
        });

    List<Transfer> transfers = new ArrayList<>();
    while (!creditors.isEmpty() && !debtors.isEmpty()) {
      Entry creditor = creditors.poll();
      Entry debtor = debtors.poll();
      BigDecimal amount = creditor.amount.min(debtor.amount);
      transfers.add(new Transfer(debtor.user, creditor.user, amount));

      BigDecimal creditorLeft = creditor.amount.subtract(amount);
      BigDecimal debtorLeft = debtor.amount.subtract(amount);
      if (creditorLeft.compareTo(EPSILON) > 0) {
        creditors.add(new Entry(creditor.user, creditorLeft));
      }
      if (debtorLeft.compareTo(EPSILON) > 0) {
        debtors.add(new Entry(debtor.user, debtorLeft));
      }
    }
    return transfers;
  }

  private record Entry(UUID user, BigDecimal amount) {}
}
