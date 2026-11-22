package com.leonardogalvao.pontocontrole.auth;

import com.leonardogalvao.pontocontrole.auth.dto.LoginResponse;
import com.leonardogalvao.pontocontrole.users.UserRole;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtTokenService(JwtEncoder jwtEncoder, JwtProperties properties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    public LoginResponse issueFor(AuthenticatedUserDetails account) {
        TokenPurpose purpose = account.role() == UserRole.FUNCIONARIO && account.mustChangePassword()
                ? TokenPurpose.PASSWORD_CHANGE
                : TokenPurpose.ACCESS;
        Duration ttl = purpose == TokenPurpose.ACCESS
                ? properties.accessTtl()
                : properties.passwordChangeTtl();
        Instant issuedAt = clock.instant();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .subject(account.id().toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(ttl))
                .id(UUID.randomUUID().toString())
                .claim("role", account.role().name())
                .claim("purpose", purpose.name())
                .build();
        JwsHeader headers = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();

        return new LoginResponse(
                token,
                "Bearer",
                purpose,
                ttl.toSeconds(),
                account.role(),
                account.mustChangePassword());
    }
}
