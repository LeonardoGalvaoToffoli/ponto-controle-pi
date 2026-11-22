package com.leonardogalvao.pontocontrole.attendance;

import com.leonardogalvao.pontocontrole.attendance.AttendanceService.CreationResult;
import com.leonardogalvao.pontocontrole.attendance.dto.AttendanceListResponse;
import com.leonardogalvao.pontocontrole.attendance.dto.AttendanceMetadata;
import com.leonardogalvao.pontocontrole.attendance.dto.AttendanceResponse;
import com.leonardogalvao.pontocontrole.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<AttendanceResponse> create(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestPart("metadata") AttendanceMetadata metadata,
            @RequestPart("photo") MultipartFile photo) {
        CreationResult result = attendanceService.create(userId(authentication), idempotencyKey, metadata, photo);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(result.attendance());
    }

    @GetMapping
    AttendanceListResponse history(
            Authentication authentication,
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return attendanceService.history(
                userId(authentication), employeeId, dateFrom, dateTo, page, size);
    }

    @GetMapping(value = "/{id}/photo", produces = MediaType.IMAGE_JPEG_VALUE)
    ResponseEntity<byte[]> photo(Authentication authentication, @PathVariable UUID id) {
        byte[] photo = attendanceService.photo(userId(authentication), id);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .contentLength(photo.length)
                .cacheControl(CacheControl.noStore())
                .body(photo);
    }

    private UUID userId(Authentication authentication) {
        try {
            return UUID.fromString(authentication.getName());
        } catch (RuntimeException exception) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "UNAUTHORIZED",
                    "A autenticação é inválida.");
        }
    }
}
