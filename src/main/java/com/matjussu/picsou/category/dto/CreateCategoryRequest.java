package com.matjussu.picsou.category.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record CreateCategoryRequest(
    @NotBlank String name, String iconKey, String colorKey, UUID parentId) {}
