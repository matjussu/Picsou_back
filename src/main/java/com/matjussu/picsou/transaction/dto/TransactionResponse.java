package com.matjussu.picsou.transaction.dto;

import com.matjussu.picsou.transaction.TransactionSource;
import com.matjussu.picsou.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionResponse(
    UUID id,
    BigDecimal amount,
    LocalDate date,
    String description,
    TransactionType type,
    TransactionSource source,
    UUID categoryId,
    UUID accountId,
    String note) {}
