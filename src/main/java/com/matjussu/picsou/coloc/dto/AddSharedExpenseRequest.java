package com.matjussu.picsou.coloc.dto;

import com.matjussu.picsou.coloc.SplitMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Ajout d'une dépense partagée. {@code participantUserIds} est utilisé en mode {@code equal} ;
 * {@code customParts} en mode {@code custom} (somme devant valoir {@code total}).
 */
public record AddSharedExpenseRequest(
    @NotNull UUID payerUserId,
    @NotNull UUID accountId,
    UUID categoryId,
    @NotBlank String description,
    @NotNull LocalDate date,
    @NotNull @Positive BigDecimal total,
    @NotNull SplitMethod splitMethod,
    List<UUID> participantUserIds,
    List<CustomPart> customParts) {

  public record CustomPart(@NotNull UUID userId, @NotNull @Positive BigDecimal amount) {}
}
