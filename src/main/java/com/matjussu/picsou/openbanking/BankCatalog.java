package com.matjussu.picsou.openbanking;

import java.util.List;
import java.util.Optional;

/**
 * Catalogue figé des banques proposées à la connexion (mock Open Banking).
 *
 * <p>Set FR représentatif (~10 marques) : grandes banques de réseau + néobanques, choisi pour un
 * rendu jury crédible. Chaque {@code slug} a un logo SVG officiel correspondant côté front ({@code
 * assets/banks/<slug>.svg}). Référentiel constant → classe utilitaire statique, pas de bean Spring.
 */
public final class BankCatalog {

  /** Ordre d'affichage : banques de réseau d'abord, néobanques ensuite. */
  private static final List<Institution> ALL =
      List.of(
          new Institution("bnp-paribas", "BNP Paribas", "#008753"),
          new Institution("societe-generale", "Société Générale", "#E9041E"),
          new Institution("credit-agricole", "Crédit Agricole", "#00965E"),
          new Institution("credit-mutuel", "Crédit Mutuel", "#D9001D"),
          new Institution("la-banque-postale", "La Banque Postale", "#003B5C"),
          new Institution("lcl", "LCL", "#0072BC"),
          new Institution("boursobank", "BoursoBank", "#EC0E73"),
          new Institution("hello-bank", "Hello bank!", "#00A3E0"),
          new Institution("revolut", "Revolut", "#0666EB"),
          new Institution("n26", "N26", "#36B49F"));

  private BankCatalog() {}

  /** Liste complète, dans l'ordre d'affichage. */
  public static List<Institution> all() {
    return ALL;
  }

  /** Recherche par slug ({@link Optional#empty()} si le slug est inconnu du catalogue). */
  public static Optional<Institution> find(String slug) {
    return ALL.stream().filter(i -> i.slug().equals(slug)).findFirst();
  }

  /** Vrai si le slug correspond à une banque du catalogue. */
  public static boolean exists(String slug) {
    return find(slug).isPresent();
  }
}
