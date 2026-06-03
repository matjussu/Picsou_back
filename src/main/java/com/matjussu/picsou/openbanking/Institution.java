package com.matjussu.picsou.openbanking;

/**
 * Entrée du catalogue d'établissements bancaires (mock Open Banking).
 *
 * <p>Référentiel statique : ce ne sont PAS des lignes en base mais une liste figée côté code. En
 * Open Banking réel, ce catalogue serait renvoyé dynamiquement par l'agrégateur (Bridge,
 * GoCardless, Tink) ; ici on le simule pour la démo jury.
 *
 * @param slug identifiant stable, kebab-case, sert de clé d'asset front ({@code
 *     assets/banks/<slug>.svg}) et de référence persistée dans {@code bank_connections}
 * @param name libellé d'affichage de la marque
 * @param brandColor couleur de marque (hex {@code #RRGGBB}) — teinte d'accent côté front, le logo
 *     officiel (SVG) portant l'identité visuelle réelle
 */
public record Institution(String slug, String name, String brandColor) {}
