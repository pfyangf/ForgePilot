package com.forgepilot.system.auth;

public record LoginResponse(String accessToken, String tokenType, AuthUser user) {
}
