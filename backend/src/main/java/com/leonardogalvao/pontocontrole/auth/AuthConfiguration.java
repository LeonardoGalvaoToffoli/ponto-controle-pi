package com.leonardogalvao.pontocontrole.auth;

import com.leonardogalvao.pontocontrole.users.UserAccount;
import com.leonardogalvao.pontocontrole.users.UserAccountRepository;
import com.leonardogalvao.pontocontrole.users.UserRole;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
public class AuthConfiguration {

    public static final String AUTHORITY_ACCESS = "TOKEN_ACCESS";
    public static final String AUTHORITY_PASSWORD_CHANGE = "TOKEN_PASSWORD_CHANGE";

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    UserDetailsService userDetailsService(UserAccountRepository userAccountRepository) {
        return username -> {
            UserAccount account = userAccountRepository.findByEmailForUpdate(username)
                    .orElseThrow(() -> new UsernameNotFoundException("Credenciais inválidas"));
            return new AuthenticatedUserDetails(account);
        };
    }

    @Bean
    AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    JwtEncoder jwtEncoder(JwtProperties properties) {
        return NimbusJwtEncoder.withSecretKey(secretKey(properties))
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        OAuth2TokenValidator<Jwt> issuerAndTime = JwtValidators.createDefaultWithIssuer(properties.issuer());
        OAuth2TokenValidator<Jwt> claims = token -> validClaims(token, properties)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new org.springframework.security.oauth2.core.OAuth2Error(
                        "invalid_token",
                        "JWT obrigatório contém claim inválida",
                        null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerAndTime, claims));
        return decoder;
    }

    @Bean
    Converter<Jwt, ? extends AbstractOAuth2TokenAuthenticationToken<Jwt>> jwtAuthenticationConverter() {
        return jwt -> new JwtAuthenticationToken(jwt, authorities(jwt), jwt.getSubject());
    }

    private SecretKey secretKey(JwtProperties properties) {
        return new SecretKeySpec(properties.secret(), "HmacSHA256");
    }

    private boolean validClaims(Jwt jwt, JwtProperties properties) {
        if (!MacAlgorithm.HS256.getName().equals(jwt.getHeaders().get("alg"))) {
            return false;
        }
        if (!jwt.getAudience().contains(properties.audience())) {
            return false;
        }
        if (!validUuid(jwt.getSubject())) {
            return false;
        }
        if (jwt.getIssuedAt() == null
                || jwt.getExpiresAt() == null
                || !jwt.getExpiresAt().isAfter(jwt.getIssuedAt())) {
            return false;
        }

        UserRole role = enumValue(UserRole.class, jwt.getClaimAsString("role"));
        TokenPurpose purpose = enumValue(TokenPurpose.class, jwt.getClaimAsString("purpose"));
        if (role == null || purpose == null) {
            return false;
        }
        return purpose != TokenPurpose.PASSWORD_CHANGE || role == UserRole.FUNCIONARIO;
    }

    private Collection<GrantedAuthority> authorities(Jwt jwt) {
        UserRole role = enumValue(UserRole.class, jwt.getClaimAsString("role"));
        TokenPurpose purpose = enumValue(TokenPurpose.class, jwt.getClaimAsString("purpose"));
        if (role == null || purpose == null) {
            return List.of();
        }

        List<GrantedAuthority> authorities = new ArrayList<>();
        if (purpose == TokenPurpose.ACCESS) {
            authorities.add(new SimpleGrantedAuthority(AUTHORITY_ACCESS));
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        } else {
            authorities.add(new SimpleGrantedAuthority(AUTHORITY_PASSWORD_CHANGE));
        }
        return List.copyOf(authorities);
    }

    private boolean validUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private <E extends Enum<E>> E enumValue(Class<E> enumClass, String value) {
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
