package com.leonardogalvao.pontocontrole.auth;

import com.leonardogalvao.pontocontrole.auth.dto.ChangePasswordRequest;
import com.leonardogalvao.pontocontrole.auth.dto.LoginRequest;
import com.leonardogalvao.pontocontrole.auth.dto.LoginResponse;
import com.leonardogalvao.pontocontrole.auth.dto.MeResponse;
import com.leonardogalvao.pontocontrole.users.UserAccount;
import com.leonardogalvao.pontocontrole.users.UserAccountRepository;
import com.leonardogalvao.pontocontrole.users.UserRole;
import com.leonardogalvao.pontocontrole.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthenticationService(
            AuthenticationManager authenticationManager,
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService) {
        this.authenticationManager = authenticationManager;
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String email = CredentialPolicy.normalizeEmail(request.email());
        if (!CredentialPolicy.isValidEmail(email) || !CredentialPolicy.isValidPassword(request.password())) {
            throw invalidCredentials();
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, request.password()));
            if (!(authentication.getPrincipal() instanceof AuthenticatedUserDetails account)) {
                throw invalidCredentials();
            }
            return jwtTokenService.issueFor(account);
        } catch (AuthenticationException exception) {
            throw invalidCredentials();
        }
    }

    @Transactional(readOnly = true)
    public MeResponse me(UUID userId) {
        UserAccount account = userAccountRepository.findById(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "UNAUTHORIZED",
                        "A autenticação não é mais válida."));
        return new MeResponse(account.getId(), account.getEmail(), account.getRole());
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        UserAccount account = userAccountRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "UNAUTHORIZED",
                        "A autenticação não é mais válida."));

        if (account.getRole() != UserRole.FUNCIONARIO || !account.isMustChangePassword()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PASSWORD_CHANGE_NOT_REQUIRED",
                    "A troca obrigatória de senha não está pendente.");
        }
        if (!CredentialPolicy.isValidPassword(request.currentPassword())
                || !passwordEncoder.matches(request.currentPassword(), account.getPasswordHash())) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_CURRENT_PASSWORD",
                    "A senha temporária atual é inválida.");
        }
        if (!CredentialPolicy.isValidPassword(request.newPassword())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PASSWORD",
                    "A nova senha deve ter entre 12 e 72 bytes em UTF-8.");
        }
        if (request.currentPassword().equals(request.newPassword())
                || passwordEncoder.matches(request.newPassword(), account.getPasswordHash())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "NEW_PASSWORD_MUST_DIFFER",
                    "A nova senha deve ser diferente da senha temporária.");
        }

        account.completePasswordChange(passwordEncoder.encode(request.newPassword()));
    }

    private ApiException invalidCredentials() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS",
                "Email ou senha inválidos.");
    }
}
