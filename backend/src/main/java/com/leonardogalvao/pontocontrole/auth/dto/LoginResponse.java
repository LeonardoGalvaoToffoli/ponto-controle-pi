package com.leonardogalvao.pontocontrole.auth.dto;

import com.leonardogalvao.pontocontrole.auth.TokenPurpose;
import com.leonardogalvao.pontocontrole.users.UserRole;

public record LoginResponse(
        String token,
        String tokenType,
        TokenPurpose tokenPurpose,
        long expiresIn,
        UserRole role,
        boolean mustChangePassword) {
}
