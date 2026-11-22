package com.leonardogalvao.pontocontrole.attendance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AttendanceRecordRepository
        extends JpaRepository<AttendanceRecord, UUID>, JpaSpecificationExecutor<AttendanceRecord> {

    List<AttendanceRecord> findAllByWorkDate(LocalDate workDate);

    java.util.Optional<AttendanceRecord> findByEmployeeIdAndIdempotencyKey(UUID employeeId, UUID idempotencyKey);

    List<AttendanceRecord> findAllByEmployeeIdAndWorkDate(UUID employeeId, LocalDate workDate);
}
