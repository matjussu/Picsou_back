package com.matjussu.picsou.auth.dto;

public record AuthResponse(String accessToken, String refreshToken, String firstName) {}
