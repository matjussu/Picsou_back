package com.matjussu.picsou.openbanking.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Résultat d'une synchronisation.
 *
 * @param connectionId connexion synchronisée
 * @param transactionsImported nombre de NOUVELLES transactions importées par cette synchro
 * @param syncedAt horodatage de fin de synchro
 */
public record SyncResultResponse(UUID connectionId, int transactionsImported, Instant syncedAt) {}
