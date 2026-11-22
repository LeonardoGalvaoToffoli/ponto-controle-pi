package com.leonardogalvao.pontocontrole.employees.dto;

import java.util.List;

public record EmployeeListResponse(
        List<EmployeeResponse> items,
        long totalEmployees,
        long enteredToday) {
}
