package com.matjussu.picsou.openbanking;

import com.matjussu.picsou.transaction.TransactionType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Générateur de transactions bancaires factices pour la démo (mock Open Banking).
 *
 * <p>Produit des libellés réalistes de relevé bancaire FR (CB, PRLV SEPA, VIR, RETRAIT DAB). La
 * sortie est <b>déterministe par connexion</b> : {@link Random} est seedé avec l'id de la
 * connexion, donc une même connexion regénère exactement le même jeu — ce qui rend l'import
 * idempotent (clé {@code external_id = mock:<connId>:<seq>}) et les tests stables.
 */
final class MockBankTransactions {

  /** Solde d'ouverture affiché pour un compte fraîchement connecté (avant net des transactions). */
  static final BigDecimal OPENING_BALANCE = new BigDecimal("2480.00");

  /** Transaction importée : libellé brut, montant, sens, ancienneté (jours), catégorie cible. */
  record MockTx(
      String label, BigDecimal amount, TransactionType type, int dayOffset, String categoryName) {}

  /**
   * Modèle de base (relevé crédible sur ~6 semaines) ; varié et sous-échantillonné par connexion.
   */
  private static final List<MockTx> TEMPLATE =
      List.of(
          tx("VIR RECU LE BON COIN", "55.00", TransactionType.income, 16, "Virement reçu"),
          tx("VIR RECU REMBOURSEMENT AMELI", "23.40", TransactionType.income, 21, "Remboursement"),
          tx("CB CARREFOUR MARKET", "47.30", TransactionType.expense, 2, "Courses"),
          tx("CB MONOPRIX", "31.85", TransactionType.expense, 9, "Courses"),
          tx("CB BOULANGERIE", "6.40", TransactionType.expense, 1, "Courses"),
          tx("PRLV SEPA EDF", "64.90", TransactionType.expense, 5, "Loyer"),
          tx("PRLV SEPA SPOTIFY", "10.99", TransactionType.expense, 6, "Streaming & Abonnements"),
          tx("PRLV SEPA NETFLIX", "13.49", TransactionType.expense, 6, "Streaming & Abonnements"),
          tx("CB SNCF CONNECT", "45.00", TransactionType.expense, 8, "Transport"),
          tx("CB TOTALENERGIES", "62.10", TransactionType.expense, 11, "Transport"),
          tx("CB UBER EATS", "23.80", TransactionType.expense, 3, "Restaurant"),
          tx("CB LE BISTROT", "38.50", TransactionType.expense, 14, "Restaurant"),
          tx("CB AMAZON FR", "34.99", TransactionType.expense, 4, "Autre"),
          tx("CB FNAC", "29.90", TransactionType.expense, 12, "Loisirs"),
          tx("CB PHARMACIE LAFAYETTE", "18.50", TransactionType.expense, 9, "Santé"),
          tx("CB DECATHLON", "39.99", TransactionType.expense, 13, "Vêtements"),
          tx("RETRAIT DAB", "40.00", TransactionType.expense, 7, "Autre"));

  private MockBankTransactions() {}

  private static MockTx tx(String label, String amount, TransactionType type, int day, String cat) {
    return new MockTx(label, new BigDecimal(amount), type, day, cat);
  }

  /**
   * Jeu déterministe de transactions pour une connexion : sous-ensemble du modèle (~10-14 lignes)
   * avec un léger jitter de montant, pour que deux banques connectées ne soient pas des copies
   * identiques tout en restant reproductibles.
   */
  static List<MockTx> generate(UUID connectionId) {
    Random rnd =
        new Random(connectionId.getMostSignificantBits() ^ connectionId.getLeastSignificantBits());
    List<MockTx> out = new ArrayList<>();
    for (MockTx t : TEMPLATE) {
      // ~15 % des lignes sont omises pour que deux banques ne soient pas des copies identiques.
      if (rnd.nextInt(100) < 15) {
        continue;
      }
      BigDecimal jittered =
          t.amount()
              .multiply(BigDecimal.valueOf(0.9 + rnd.nextDouble() * 0.2))
              .setScale(2, RoundingMode.HALF_UP);
      out.add(new MockTx(t.label(), jittered, t.type(), t.dayOffset(), t.categoryName()));
    }
    return out;
  }
}
