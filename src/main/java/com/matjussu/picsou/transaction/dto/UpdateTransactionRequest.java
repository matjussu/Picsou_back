package com.matjussu.picsou.transaction.dto;

import com.matjussu.picsou.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record UpdateTransactionRequest(
    BigDecimal amount,
    LocalDate date,
    String description,
    TransactionType type,
    UUID categoryId,
    String note) {}
