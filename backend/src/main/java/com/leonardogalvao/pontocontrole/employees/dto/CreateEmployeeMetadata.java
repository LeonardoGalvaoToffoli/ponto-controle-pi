package com.leonardogalvao.pontocontrole.employees.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateEmployeeMetadata(
        @NotBlank String name,
        @NotBlank String registrationNumber,
        @NotBlank String jobTitle,
        @NotBlank String email,
        @NotNull String temporaryPassword) {
}
