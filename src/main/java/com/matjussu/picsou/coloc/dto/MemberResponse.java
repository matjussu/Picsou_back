package com.matjussu.picsou.coloc.dto;

import com.matjussu.picsou.coloc.ColocRole;
import java.util.UUID;

public record MemberResponse(UUID userId, String email, String firstName, ColocRole role) {}
