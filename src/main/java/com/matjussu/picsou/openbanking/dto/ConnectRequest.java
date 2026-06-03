package com.matjussu.picsou.openbanking.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Requête de connexion à une banque.
 *
 * @param institutionId slug de la banque dans le catalogue (GET /api/openbanking/institutions)
 */
public record ConnectRequest(@NotBlank String institutionId) {}
