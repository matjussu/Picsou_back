package com.matjussu.picsou.coloc.dto;

import java.util.List;
import java.util.UUID;

public record GroupDetailResponse(UUID id, String name, List<MemberResponse> members) {}
