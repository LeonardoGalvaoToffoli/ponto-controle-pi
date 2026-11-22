package com.leonardogalvao.pontocontrole.auth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotNull @Size(min = 1, max = 72) String currentPassword,
        @NotNull @Size(min = 1, max = 72) String newPassword) {
}
