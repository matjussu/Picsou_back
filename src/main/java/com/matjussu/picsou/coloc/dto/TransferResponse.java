package com.matjussu.picsou.coloc.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Virement suggéré (présentationnel) : {@code from} doit {@code amount} à {@code to}. */
public record TransferResponse(
    UUID fromUserId, String fromName, UUID toUserId, String toName, BigDecimal amount) {}
