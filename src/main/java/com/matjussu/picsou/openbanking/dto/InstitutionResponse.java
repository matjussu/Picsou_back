package com.matjussu.picsou.openbanking.dto;

/**
 * Banque exposée au front pour la grille de connexion.
 *
 * @param slug clé stable (= nom de l'asset SVG côté front : {@code assets/banks/<slug>.svg})
 * @param name libellé d'affichage
 * @param brandColor couleur de marque hex {@code #RRGGBB}
 */
public record InstitutionResponse(String slug, String name, String brandColor) {}
