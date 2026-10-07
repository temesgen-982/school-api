package com.school.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.school.api.common.domain.Role;
import com.school.api.common.domain.Status;
import com.school.api.user.UserAccount;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    private final JwtConfig config = new JwtConfig();
    private final JwtProperties properties = new JwtProperties(SECRET, Duration.ofMinutes(15));
    private final SecretKey key = config.jwtSecretKey(properties);
    private final JwtService jwtService = new JwtService(config.jwtEncoder(key), properties);
    private final JwtDecoder jwtDecoder = config.jwtDecoder(key);

    @Test
    void issueCreatesDecodableTokenWithRolesAndSchool() {
        UUID schoolId = UUID.randomUUID();

        JwtService.IssuedToken issued = jwtService.issue(account(Role.SCHOOL_ADMIN, schoolId));
        Jwt jwt = jwtDecoder.decode(issued.accessToken());

        assertThat(jwt.getSubject()).isEqualTo("school-admin");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("SCHOOL_ADMIN");
        assertThat(jwt.getClaimAsString("school_id")).isEqualTo(schoolId.toString());
        assertThat(jwt.getId()).isNotNull();
        assertThat(jwt.getExpiresAt()).isEqualTo(issued.expiresAt().truncatedTo(ChronoUnit.SECONDS));
    }

    @Test
    void tokenWithoutRolesIsRejectedByDecoder() {
        UserAccount account = account(Role.PLATFORM_ADMIN, null);
        account.setRoles(Set.of());

        String token = jwtService.issue(account).accessToken();

        assertThatThrownBy(() -> jwtDecoder.decode(token))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("Token has no roles");
    }

    @Test
    void expiredTokenIsRejectedByDecoder() {
        Instant past = Instant.now().minusSeconds(7200);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("expired-user")
                .issuedAt(past)
                .expiresAt(past.plusSeconds(3600))
                .claim("roles", Set.of(Role.PLATFORM_ADMIN.name()))
                .build();
        JwtEncoder encoder = NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        assertThatThrownBy(() -> jwtDecoder.decode(token)).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        SecretKey otherKey = config.jwtSecretKey(new JwtProperties("another-secret-another-secret-32ch",
                Duration.ofMinutes(15)));
        JwtService otherService = new JwtService(config.jwtEncoder(otherKey), properties);

        String token = otherService.issue(account(Role.PLATFORM_ADMIN, null)).accessToken();

        assertThatThrownBy(() -> jwtDecoder.decode(token)).isInstanceOf(BadJwtException.class);
    }

    private static UserAccount account(Role role, UUID schoolId) {
        UserAccount account = new UserAccount();
        account.setUsername("school-admin");
        account.setStatus(Status.ACTIVE);
        account.setSchoolId(schoolId);
        account.setRoles(Set.of(role));
        return account;
    }
}
