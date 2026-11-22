package com.leonardogalvao.pontocontrole.attendance;

import com.leonardogalvao.pontocontrole.attendance.dto.AttendanceListResponse;
import com.leonardogalvao.pontocontrole.attendance.dto.AttendanceMetadata;
import com.leonardogalvao.pontocontrole.attendance.dto.AttendanceResponse;
import com.leonardogalvao.pontocontrole.config.WorkplaceProperties;
import com.leonardogalvao.pontocontrole.employees.EmployeeProfile;
import com.leonardogalvao.pontocontrole.employees.EmployeeProfileRepository;
import com.leonardogalvao.pontocontrole.storage.PrivatePhotoStorage;
import com.leonardogalvao.pontocontrole.storage.PrivatePhotoStorage.PhotoPayload;
import com.leonardogalvao.pontocontrole.storage.PrivatePhotoStorage.StoredPhoto;
import com.leonardogalvao.pontocontrole.users.UserAccount;
import com.leonardogalvao.pontocontrole.users.UserAccountRepository;
import com.leonardogalvao.pontocontrole.users.UserRole;
import com.leonardogalvao.pontocontrole.web.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class AttendanceService {

    private static final ZoneId WORKPLACE_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private final AttendanceRecordRepository attendanceRepository;
    private final EmployeeProfileRepository employeeRepository;
    private final UserAccountRepository userRepository;
    private final PrivatePhotoStorage photoStorage;
    private final WorkplaceProperties workplace;
    private final Clock clock;

    public AttendanceService(
            AttendanceRecordRepository attendanceRepository,
            EmployeeProfileRepository employeeRepository,
            UserAccountRepository userRepository,
            PrivatePhotoStorage photoStorage,
            WorkplaceProperties workplace,
            Clock clock) {
        this.attendanceRepository = attendanceRepository;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.photoStorage = photoStorage;
        this.workplace = workplace;
        this.clock = clock;
    }

    @Transactional
    public CreationResult create(
            UUID employeeId,
            UUID idempotencyKey,
            AttendanceMetadata metadata,
            MultipartFile photo) {
        PhotoPayload photoPayload = photoStorage.validateJpeg(photo);
        String fingerprint = fingerprint(metadata, photoPayload.sha256());

        EmployeeProfile employee = employeeRepository.findByIdForUpdate(employeeId)
                .orElseThrow(this::employeeNotFound);

        AttendanceRecord existing = attendanceRepository
                .findByEmployeeIdAndIdempotencyKey(employee.getUserId(), idempotencyKey)
                .orElse(null);
        if (existing != null) {
            if (!MessageDigest.isEqual(
                    existing.getRequestFingerprint().getBytes(StandardCharsets.US_ASCII),
                    fingerprint.getBytes(StandardCharsets.US_ASCII))) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "IDEMPOTENCY_CONFLICT",
                        "Esta chave de tentativa já foi usada com outros dados.");
            }
            return new CreationResult(response(existing), true);
        }

        validateLocationFields(metadata);
        Instant now = clock.instant();
        validateRecency(metadata.capturedAt(), now);
        double distance = haversineMeters(metadata.latitude(), metadata.longitude());
        if (distance > workplace.radiusMeters().doubleValue()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "GPS_OUT_OF_RANGE",
                    "A localização está fora do raio permitido para o ponto.");
        }

        LocalDate workDate = LocalDate.ofInstant(now, WORKPLACE_ZONE);
        validateSequence(
                metadata.type(),
                attendanceRepository.findAllByEmployeeIdAndWorkDate(employeeId, workDate));

        UUID attendanceId = UUID.randomUUID();
        StoredPhoto storedPhoto = photoStorage.storeAttendancePhoto(employeeId, attendanceId, photoPayload);
        registerRollbackCompensation(storedPhoto.key());

        AttendanceRecord record = new AttendanceRecord(
                attendanceId,
                employeeId,
                metadata.type(),
                now,
                workDate,
                metadata.capturedAt(),
                normalizedDecimal(metadata.latitude()),
                normalizedDecimal(metadata.longitude()),
                normalizedDecimal(metadata.accuracyMeters()),
                BigDecimal.valueOf(distance).setScale(3, RoundingMode.HALF_UP),
                storedPhoto.key(),
                photoPayload.sha256(),
                idempotencyKey,
                fingerprint);
        try {
            attendanceRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "INVALID_SEQUENCE",
                    "Já existe um registro incompatível com esta tentativa.");
        }
        return new CreationResult(response(record), false);
    }

    @Transactional(readOnly = true)
    public AttendanceListResponse history(
            UUID requesterId,
            UUID requestedEmployeeId,
            LocalDate dateFrom,
            LocalDate dateTo,
            int page,
            int size) {
        UserAccount requester = requiredRequester(requesterId);
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_DATE_RANGE",
                    "A data inicial não pode ser posterior à data final.");
        }

        UUID employeeId = requestedEmployeeId;
        if (requester.getRole() == UserRole.FUNCIONARIO) {
            if (requestedEmployeeId != null) {
                throw new ApiException(
                        HttpStatus.FORBIDDEN,
                        "EMPLOYEE_FILTER_FORBIDDEN",
                        "Funcionários não podem consultar o histórico de outra conta.");
            }
            employeeId = requesterId;
        }

        List<Specification<AttendanceRecord>> filters = new ArrayList<>();
        if (employeeId != null) {
            UUID selectedEmployee = employeeId;
            filters.add((root, query, builder) -> builder.equal(root.get("employeeId"), selectedEmployee));
        }
        if (dateFrom != null) {
            filters.add((root, query, builder) -> builder.greaterThanOrEqualTo(root.get("workDate"), dateFrom));
        }
        if (dateTo != null) {
            filters.add((root, query, builder) -> builder.lessThanOrEqualTo(root.get("workDate"), dateTo));
        }

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("recordedAt"), Sort.Order.desc("id")));
        Page<AttendanceRecord> records = attendanceRepository.findAll(Specification.allOf(filters), pageable);
        return new AttendanceListResponse(
                records.getContent().stream().map(this::response).toList(),
                records.getNumber(),
                records.getSize(),
                records.getTotalElements());
    }

    @Transactional(readOnly = true)
    public byte[] photo(UUID requesterId, UUID attendanceId) {
        UserAccount requester = requiredRequester(requesterId);
        AttendanceRecord record = attendanceRepository.findById(attendanceId)
                .orElseThrow(this::attendanceNotFound);
        if (requester.getRole() == UserRole.FUNCIONARIO
                && !requesterId.equals(record.getEmployeeId())) {
            throw attendanceNotFound();
        }
        return photoStorage.read(record.getPhotoKey());
    }

    private void validateLocationFields(AttendanceMetadata metadata) {
        if (metadata.latitude().compareTo(BigDecimal.valueOf(-90)) < 0
                || metadata.latitude().compareTo(BigDecimal.valueOf(90)) > 0
                || metadata.longitude().compareTo(BigDecimal.valueOf(-180)) < 0
                || metadata.longitude().compareTo(BigDecimal.valueOf(180)) > 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_COORDINATES",
                    "As coordenadas informadas são inválidas.");
        }
        if (metadata.accuracyMeters().compareTo(BigDecimal.ZERO) <= 0
                || metadata.accuracyMeters().compareTo(workplace.maxAccuracyMeters()) > 0) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "GPS_INACCURATE",
                    "A precisão do GPS não é suficiente para registrar o ponto.");
        }
    }

    private void validateRecency(Instant capturedAt, Instant now) {
        if (capturedAt.isBefore(now.minusSeconds(60)) || capturedAt.isAfter(now.plusSeconds(10))) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "GPS_STALE",
                    "A localização não é recente; obtenha uma nova leitura do GPS.");
        }
    }

    private void validateSequence(AttendanceType requestedType, List<AttendanceRecord> records) {
        boolean hasEntry = records.stream().anyMatch(record -> record.getType() == AttendanceType.ENTRADA);
        boolean hasExit = records.stream().anyMatch(record -> record.getType() == AttendanceType.SAIDA);
        boolean valid = requestedType == AttendanceType.ENTRADA
                ? !hasEntry && !hasExit
                : hasEntry && !hasExit;
        if (!valid) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "INVALID_SEQUENCE",
                    requestedType == AttendanceType.SAIDA && !hasEntry
                            ? "Registre a entrada antes da saída."
                            : "Este tipo de ponto já foi registrado hoje.");
        }
    }

    private double haversineMeters(BigDecimal latitude, BigDecimal longitude) {
        double latitude1 = Math.toRadians(latitude.doubleValue());
        double longitude1 = Math.toRadians(longitude.doubleValue());
        double latitude2 = Math.toRadians(workplace.latitude().doubleValue());
        double longitude2 = Math.toRadians(workplace.longitude().doubleValue());
        double latitudeDelta = latitude2 - latitude1;
        double longitudeDelta = longitude2 - longitude1;
        double a = Math.pow(Math.sin(latitudeDelta / 2), 2)
                + Math.cos(latitude1) * Math.cos(latitude2)
                * Math.pow(Math.sin(longitudeDelta / 2), 2);
        return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private String fingerprint(AttendanceMetadata metadata, String photoSha256) {
        String canonical = String.join("\n",
                metadata.type().name(),
                canonicalDecimal(metadata.latitude()),
                canonicalDecimal(metadata.longitude()),
                canonicalDecimal(metadata.accuracyMeters()),
                metadata.capturedAt().toString(),
                photoSha256);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível", exception);
        }
    }

    private String canonicalDecimal(BigDecimal value) {
        BigDecimal canonical = value.stripTrailingZeros();
        return canonical.signum() == 0 ? "0" : canonical.toPlainString();
    }

    private BigDecimal normalizedDecimal(BigDecimal value) {
        return value.stripTrailingZeros();
    }

    private AttendanceResponse response(AttendanceRecord record) {
        return new AttendanceResponse(
                record.getId(),
                record.getEmployeeId(),
                record.getType(),
                record.getRecordedAt(),
                record.getWorkDate(),
                record.getDistanceMeters(),
                "/api/v1/attendance/" + record.getId() + "/photo");
    }

    private UserAccount requiredRequester(UUID requesterId) {
        return userRepository.findById(requesterId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "UNAUTHORIZED",
                        "A autenticação não é mais válida."));
    }

    private void registerRollbackCompensation(String photoKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            photoStorage.deleteIfExists(photoKey);
            throw new IllegalStateException("Registro fora de uma transação ativa");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    photoStorage.deleteIfExists(photoKey);
                }
            }
        });
    }

    private ApiException employeeNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "EMPLOYEE_NOT_FOUND",
                "Funcionário não encontrado.");
    }

    private ApiException attendanceNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "ATTENDANCE_NOT_FOUND",
                "Registro de ponto não encontrado.");
    }

    public record CreationResult(AttendanceResponse attendance, boolean replayed) {
    }
}
