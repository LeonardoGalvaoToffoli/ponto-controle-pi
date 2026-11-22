package com.leonardogalvao.pontocontrole.attendance.dto;

import com.leonardogalvao.pontocontrole.attendance.AttendanceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AttendanceResponse(
        UUID id,
        UUID employeeId,
        AttendanceType type,
        Instant recordedAt,
        LocalDate workDate,
        BigDecimal distanceMeters,
        String photoUrl) {
}
