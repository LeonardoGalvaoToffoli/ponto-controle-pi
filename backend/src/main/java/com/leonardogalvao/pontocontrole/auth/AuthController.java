package com.leonardogalvao.pontocontrole.auth;

import com.leonardogalvao.pontocontrole.auth.dto.ChangePasswordRequest;
import com.leonardogalvao.pontocontrole.auth.dto.LoginRequest;
import com.leonardogalvao.pontocontrole.auth.dto.LoginResponse;
import com.leonardogalvao.pontocontrole.auth.dto.MeResponse;
import com.leonardogalvao.pontocontrole.web.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final AuthenticationService authenticationService;

    public AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/auth/login")
    ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(authenticationService.login(request));
    }

    @PostMapping("/auth/change-password")
    ResponseEntity<Void> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request) {
        authenticationService.changePassword(userId(authentication), request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    MeResponse me(Authentication authentication) {
        return authenticationService.me(userId(authentication));
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
