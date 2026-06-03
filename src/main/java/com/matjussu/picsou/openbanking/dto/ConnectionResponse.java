package com.matjussu.picsou.openbanking.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Connexion bancaire telle qu'affichée côté front (carte « banque connectée »).
 *
 * @param id identifiant de la connexion
 * @param institutionSlug slug de la banque (clé de l'asset logo : {@code assets/banks/<slug>.svg})
 * @param institutionName libellé de la banque
 * @param brandColor couleur de marque hex {@code #RRGGBB}
 * @param status statut de la connexion ({@code active} / {@code expired} / {@code revoked})
 * @param accountId compte Picsou alimenté par cette connexion
 * @param transactionsImported nombre de transactions importées
 * @param lastSyncAt horodatage de la dernière synchro réussie ({@code null} si jamais synchronisée)
 * @param connectedAt date de connexion initiale
 */
public record ConnectionResponse(
    UUID id,
    String institutionSlug,
    String institutionName,
    String brandColor,
    String status,
    UUID accountId,
    long transactionsImported,
    Instant lastSyncAt,
    Instant connectedAt) {}
