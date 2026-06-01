package com.matjussu.picsou.category.dto;

import java.util.UUID;

public record CategoryResponse(
    UUID id, String name, String iconKey, String colorKey, boolean isDefault, UUID parentId) {}
