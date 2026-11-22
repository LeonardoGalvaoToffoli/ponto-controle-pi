package com.leonardogalvao.pontocontrole.attendance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "attendance_records",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_attendance_employee_work_date_type",
                        columnNames = {"employee_id", "work_date", "type"}),
                @UniqueConstraint(
                        name = "uk_attendance_employee_idempotency",
                        columnNames = {"employee_id", "idempotency_key"})
        })
public class AttendanceRecord {

    @Id
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AttendanceType type;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "gps_captured_at", nullable = false)
    private Instant gpsCapturedAt;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "accuracy_meters", nullable = false, precision = 8, scale = 3)
    private BigDecimal accuracyMeters;

    @Column(name = "distance_meters", nullable = false, precision = 10, scale = 3)
    private BigDecimal distanceMeters;

    @Column(name = "photo_key", nullable = false, length = 255)
    private String photoKey;

    @Column(name = "photo_sha256", nullable = false, length = 64)
    private String photoSha256;

    @Column(name = "idempotency_key", nullable = false)
    private UUID idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    protected AttendanceRecord() {
    }

    public AttendanceRecord(
            UUID id,
            UUID employeeId,
            AttendanceType type,
            Instant recordedAt,
            LocalDate workDate,
            Instant gpsCapturedAt,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal accuracyMeters,
            BigDecimal distanceMeters,
            String photoKey,
            String photoSha256,
            UUID idempotencyKey,
            String requestFingerprint) {
        this.id = id;
        this.employeeId = employeeId;
        this.type = type;
        this.recordedAt = recordedAt;
        this.workDate = workDate;
        this.gpsCapturedAt = gpsCapturedAt;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracyMeters = accuracyMeters;
        this.distanceMeters = distanceMeters;
        this.photoKey = photoKey;
        this.photoSha256 = photoSha256;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public AttendanceType getType() {
        return type;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public BigDecimal getDistanceMeters() {
        return distanceMeters;
    }

    public String getPhotoKey() {
        return photoKey;
    }

    public String getPhotoSha256() {
        return photoSha256;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }
}
