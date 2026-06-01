package com.matjussu.picsou.account.dto;

import com.matjussu.picsou.account.AccountType;
import java.math.BigDecimal;
import java.util.UUID;

public record AccountResponse(
    UUID id, String name, AccountType type, BigDecimal balance, String currency) {}
