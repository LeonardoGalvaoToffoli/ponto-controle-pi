package com.leonardogalvao.pontocontrole.attendance.dto;

import com.leonardogalvao.pontocontrole.attendance.AttendanceType;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public record AttendanceMetadata(
        @NotNull AttendanceType type,
        @NotNull BigDecimal latitude,
        @NotNull BigDecimal longitude,
        @NotNull BigDecimal accuracyMeters,
        @NotNull Instant capturedAt) {
}
