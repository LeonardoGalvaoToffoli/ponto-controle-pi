package com.leonardogalvao.pontocontrole.employees;

import com.leonardogalvao.pontocontrole.attendance.AttendanceRecord;
import com.leonardogalvao.pontocontrole.attendance.AttendanceRecordRepository;
import com.leonardogalvao.pontocontrole.attendance.AttendanceType;
import com.leonardogalvao.pontocontrole.auth.CredentialPolicy;
import com.leonardogalvao.pontocontrole.employees.dto.CreateEmployeeMetadata;
import com.leonardogalvao.pontocontrole.employees.dto.EmployeeListResponse;
import com.leonardogalvao.pontocontrole.employees.dto.EmployeeResponse;
import com.leonardogalvao.pontocontrole.storage.PrivatePhotoStorage;
import com.leonardogalvao.pontocontrole.storage.PrivatePhotoStorage.StoredPhoto;
import com.leonardogalvao.pontocontrole.users.UserAccount;
import com.leonardogalvao.pontocontrole.users.UserAccountRepository;
import com.leonardogalvao.pontocontrole.users.UserRole;
import com.leonardogalvao.pontocontrole.web.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class EmployeeService {

    private static final Pattern REGISTRATION_PATTERN = Pattern.compile("^[A-Z0-9._-]{1,30}$");
    private static final ZoneId WORKPLACE_ZONE = ZoneId.of("America/Sao_Paulo");

    private final EmployeeProfileRepository employeeRepository;
    private final UserAccountRepository userRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final PrivatePhotoStorage photoStorage;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public EmployeeService(
            EmployeeProfileRepository employeeRepository,
            UserAccountRepository userRepository,
            AttendanceRecordRepository attendanceRepository,
            PrivatePhotoStorage photoStorage,
            PasswordEncoder passwordEncoder,
            Clock clock) {
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.attendanceRepository = attendanceRepository;
        this.photoStorage = photoStorage;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public EmployeeResponse create(CreateEmployeeMetadata metadata, MultipartFile photo) {
        NormalizedEmployee normalized = normalizeAndValidate(metadata);
        rejectKnownDuplicates(normalized);

        UUID employeeId = UUID.randomUUID();
        StoredPhoto storedPhoto = photoStorage.storeProfilePhoto(employeeId, photo);
        registerRollbackCompensation(storedPhoto.key());

        UserAccount account = new UserAccount(
                employeeId,
                normalized.email(),
                passwordEncoder.encode(metadata.temporaryPassword()),
                UserRole.FUNCIONARIO,
                true,
                clock.instant());
        EmployeeProfile profile = new EmployeeProfile(
                employeeId,
                normalized.registrationNumber(),
                normalized.name(),
                normalized.jobTitle(),
                storedPhoto.key());

        try {
            userRepository.save(account);
            employeeRepository.saveAndFlush(profile);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "EMPLOYEE_ALREADY_EXISTS",
                    "Já existe um funcionário com este email ou matrícula.");
        }

        return response(profile, account, TodayStatus.SEM_REGISTRO);
    }

    @Transactional(readOnly = true)
    public EmployeeListResponse list() {
        List<EmployeeProfile> profiles = employeeRepository.findAllByOrderByNameAsc();
        Map<UUID, UserAccount> accounts = userRepository.findAllById(
                        profiles.stream().map(EmployeeProfile::getUserId).toList())
                .stream()
                .collect(Collectors.toMap(UserAccount::getId, Function.identity()));
        Map<UUID, TodayStatus> statuses = todayStatuses();

        List<EmployeeResponse> items = profiles.stream()
                .map(profile -> response(
                        profile,
                        requiredAccount(accounts, profile.getUserId()),
                        statuses.getOrDefault(profile.getUserId(), TodayStatus.SEM_REGISTRO)))
                .toList();
        long enteredToday = statuses.values().stream()
                .filter(status -> status != TodayStatus.SEM_REGISTRO)
                .count();
        return new EmployeeListResponse(items, items.size(), enteredToday);
    }

    @Transactional(readOnly = true)
    public EmployeeResponse profile(UUID employeeId) {
        EmployeeProfile profile = employeeRepository.findById(employeeId)
                .orElseThrow(this::employeeNotFound);
        UserAccount account = userRepository.findById(employeeId)
                .orElseThrow(this::employeeNotFound);
        TodayStatus status = todayStatuses().getOrDefault(employeeId, TodayStatus.SEM_REGISTRO);
        return response(profile, account, status);
    }

    @Transactional(readOnly = true)
    public byte[] profilePhoto(UUID requesterId, UUID employeeId) {
        UserAccount requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "UNAUTHORIZED",
                        "A autenticação não é mais válida."));
        if (requester.getRole() == UserRole.FUNCIONARIO && !requesterId.equals(employeeId)) {
            throw employeeNotFound();
        }

        EmployeeProfile profile = employeeRepository.findById(employeeId)
                .orElseThrow(this::employeeNotFound);
        return photoStorage.read(profile.getProfilePhotoKey());
    }

    private NormalizedEmployee normalizeAndValidate(CreateEmployeeMetadata metadata) {
        String name = normalizeText(metadata.name());
        String jobTitle = normalizeText(metadata.jobTitle());
        String registration = metadata.registrationNumber() == null
                ? null
                : metadata.registrationNumber().strip().toUpperCase(Locale.ROOT);
        String email = CredentialPolicy.normalizeEmail(metadata.email());

        if (!validLength(name, 2, 120)
                || !validLength(jobTitle, 2, 80)
                || registration == null
                || !REGISTRATION_PATTERN.matcher(registration).matches()
                || !CredentialPolicy.isValidEmail(email)
                || !CredentialPolicy.isValidPassword(metadata.temporaryPassword())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_EMPLOYEE",
                    "Confira nome, matrícula, cargo, email e senha temporária.");
        }
        return new NormalizedEmployee(name, registration, jobTitle, email);
    }

    private void rejectKnownDuplicates(NormalizedEmployee employee) {
        if (userRepository.existsByEmail(employee.email())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "DUPLICATE_EMAIL",
                    "Já existe uma conta com este email.");
        }
        if (employeeRepository.existsByRegistrationNumber(employee.registrationNumber())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "DUPLICATE_REGISTRATION",
                    "Já existe um funcionário com esta matrícula.");
        }
    }

    private Map<UUID, TodayStatus> todayStatuses() {
        LocalDate today = LocalDate.now(clock.withZone(WORKPLACE_ZONE));
        Map<UUID, TodayStatus> statuses = new HashMap<>();
        for (AttendanceRecord record : attendanceRepository.findAllByWorkDate(today)) {
            if (record.getType() == AttendanceType.SAIDA) {
                statuses.put(record.getEmployeeId(), TodayStatus.SAIDA_REGISTRADA);
            } else {
                statuses.putIfAbsent(record.getEmployeeId(), TodayStatus.ENTRADA_REGISTRADA);
            }
        }
        return statuses;
    }

    private EmployeeResponse response(
            EmployeeProfile profile,
            UserAccount account,
            TodayStatus todayStatus) {
        UUID id = profile.getUserId();
        return new EmployeeResponse(
                id,
                account.getEmail(),
                profile.getName(),
                profile.getRegistrationNumber(),
                profile.getJobTitle(),
                "/api/v1/employees/" + id + "/photo",
                todayStatus);
    }

    private UserAccount requiredAccount(Map<UUID, UserAccount> accounts, UUID id) {
        UserAccount account = accounts.get(id);
        if (account == null) {
            throw new IllegalStateException("Funcionário sem conta associada");
        }
        return account;
    }

    private void registerRollbackCompensation(String photoKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            photoStorage.deleteIfExists(photoKey);
            throw new IllegalStateException("Cadastro fora de uma transação ativa");
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

    private String normalizeText(String value) {
        return value == null ? null : value.strip();
    }

    private boolean validLength(String value, int minimum, int maximum) {
        return value != null && value.length() >= minimum && value.length() <= maximum;
    }

    private ApiException employeeNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "EMPLOYEE_NOT_FOUND",
                "Funcionário não encontrado.");
    }

    private record NormalizedEmployee(
            String name,
            String registrationNumber,
            String jobTitle,
            String email) {
    }
}
