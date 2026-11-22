package com.leonardogalvao.pontocontrole.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Base64;

@ConfigurationProperties("app.jwt")
public final class JwtProperties {

    private final byte[] secret;
    private final String issuer;
    private final String audience;
    private final Duration accessTtl;
    private final Duration passwordChangeTtl;

    public JwtProperties(
            String secretBase64,
            String issuer,
            String audience,
            Duration accessTtl,
            Duration passwordChangeTtl) {
        this.secret = decodeAndValidateSecret(secretBase64);
        this.issuer = requiredText(issuer, "O emissor JWT deve ser configurado");
        this.audience = requiredText(audience, "A audiência JWT deve ser configurada");
        this.accessTtl = positiveDuration(accessTtl, "A validade do token de acesso deve ser positiva");
        this.passwordChangeTtl = positiveDuration(
                passwordChangeTtl,
                "A validade do token de troca de senha deve ser positiva");
    }

    public byte[] secret() {
        return secret.clone();
    }

    public String issuer() {
        return issuer;
    }

    public String audience() {
        return audience;
    }

    public Duration accessTtl() {
        return accessTtl;
    }

    public Duration passwordChangeTtl() {
        return passwordChangeTtl;
    }

    private byte[] decodeAndValidateSecret(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("JWT_SECRET_BASE64 deve ser configurado");
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            if (decoded.length < 32) {
                throw new IllegalArgumentException("JWT_SECRET_BASE64 deve conter pelo menos 32 bytes");
            }
            return decoded;
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null && exception.getMessage().startsWith("JWT_SECRET_BASE64")) {
                throw exception;
            }
            throw new IllegalArgumentException("JWT_SECRET_BASE64 deve ser Base64 válido", exception);
        }
    }

    private String requiredText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private Duration positiveDuration(Duration value, String message) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
