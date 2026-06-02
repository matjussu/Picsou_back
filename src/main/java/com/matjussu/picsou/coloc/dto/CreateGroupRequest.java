package com.matjussu.picsou.coloc.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateGroupRequest(@NotBlank String name) {}
