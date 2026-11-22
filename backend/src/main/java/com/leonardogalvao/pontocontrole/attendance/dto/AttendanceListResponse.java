package com.leonardogalvao.pontocontrole.attendance.dto;

import java.util.List;

public record AttendanceListResponse(
        List<AttendanceResponse> items,
        int page,
        int size,
        long totalItems) {
}
