package com.leonardogalvao.pontocontrole.employees.dto;

import com.leonardogalvao.pontocontrole.employees.TodayStatus;

import java.util.UUID;

public record EmployeeResponse(
        UUID id,
        String email,
        String name,
        String registrationNumber,
        String jobTitle,
        String profilePhotoUrl,
        TodayStatus todayStatus) {
}
