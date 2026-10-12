package com.campus.complaints;

public record AuthResponse(String accessToken, String tokenType, UserProfileResponse user) {
}
