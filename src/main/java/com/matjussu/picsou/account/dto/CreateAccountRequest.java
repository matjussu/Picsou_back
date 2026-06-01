package com.matjussu.picsou.account.dto;

import com.matjussu.picsou.account.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateAccountRequest(@NotBlank String name, @NotNull AccountType type) {}
