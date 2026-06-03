package com.matjussu.picsou.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Question libre de l'utilisateur sur ses finances.
 *
 * @param question texte de la question (non vide, borné pour limiter le coût/abus)
 */
public record AskRequest(@NotBlank @Size(max = 500) String question) {}
