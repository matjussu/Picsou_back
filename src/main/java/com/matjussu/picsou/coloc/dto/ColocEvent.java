package com.matjussu.picsou.coloc.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Événement temps réel diffusé sur {@code /topic/coloc/{groupId}}.
 *
 * @param type {@code expense.added} | {@code expense.settled}
 * @param actorName prénom de l'auteur de l'action
 * @param description libellé (ex. description de la dépense)
 * @param amount montant concerné (nullable)
 * @param at horodatage serveur
 */
public record ColocEvent(
    String type, String actorName, String description, BigDecimal amount, Instant at) {}
