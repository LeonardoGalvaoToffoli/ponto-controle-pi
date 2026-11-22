package com.leonardogalvao.pontocontrole.auth.dto;

import com.leonardogalvao.pontocontrole.users.UserRole;

import java.util.UUID;

public record MeResponse(UUID id, String email, UserRole role) {
}
