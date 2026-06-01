package com.matjussu.picsou.transaction.dto;

import com.matjussu.picsou.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Critères de filtrage des transactions — tous optionnels, appliqués côté backend. */
public record TransactionFilter(
    LocalDate from,
    LocalDate to,
    UUID categoryId,
    UUID accountId,
    BigDecimal minAmount,
    BigDecimal maxAmount,
    TransactionType type,
    String q) {}
