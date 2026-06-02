package com.matjussu.picsou.coloc.dto;

import com.matjussu.picsou.coloc.SplitMethod;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Vue d'une dépense partagée orientée user courant ({@code yourShare} = sa part, null si aucune).
 */
public record SharedExpenseResponse(
    UUID id,
    UUID transactionId,
    UUID payerUserId,
    String payerName,
    String description,
    LocalDate date,
    BigDecimal total,
    SplitMethod splitMethod,
    boolean settled,
    BigDecimal yourShare) {}
