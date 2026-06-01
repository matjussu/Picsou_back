package com.matjussu.picsou.transaction.dto;

import com.matjussu.picsou.transaction.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateTransactionRequest(
    @NotNull @Positive BigDecimal amount,
    @NotNull LocalDate date,
    @NotBlank String description,
    @NotNull TransactionType type,
    UUID categoryId,
    UUID accountId,
    String note) {}
