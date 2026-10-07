package com.school.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.school.api.common.domain.Role;
import com.school.api.common.domain.Status;
import com.school.api.common.web.ApiPaths;
import com.school.api.user.UserAccount;
import com.school.api.user.UserAccountRepository;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end check of the auth plumbing against the real database: login issues a
 * token the resource server accepts, and the role rules are enforced on the wire.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

    private static final String SCHOOLS = ApiPaths.V1 + "/schools";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SecretKey jwtSecretKey;

    @Autowired
    private JwtDecoder jwtDecoder;

    private UserAccount createdUser;

    @AfterEach
    void cleanUp() {
        if (createdUser != null) {
            userAccountRepository.delete(createdUser);
            createdUser = null;
        }
    }

    @Test
    void loginIssuesTokenThatPassesSecurityRules() throws Exception {
        String username = newUsername();
        createdUser = saveUser(username, "s3cret-pass", Role.SCHOOL_ADMIN);

        String token = login(username, "s3cret-pass");
        Jwt jwt = jwtDecoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo(username);
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("SCHOOL_ADMIN");

        mockMvc.perform(get(SCHOOLS).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get(SCHOOLS))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(SCHOOLS)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Should Not Create","code":"should-not-create"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        String username = newUsername();
        createdUser = saveUser(username, "s3cret-pass", Role.SCHOOL_ADMIN);

        mockMvc.perform(post(AuthController.LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"wrong"}
                                """.formatted(username)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    void malformedTokenReturns401() throws Exception {
        mockMvc.perform(get(SCHOOLS).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenReturns401() throws Exception {
        Instant past = Instant.now().minusSeconds(7200);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("expired-user")
                .issuedAt(past)
                .expiresAt(past.plusSeconds(3600))
                .claim("roles", Set.of(Role.PLATFORM_ADMIN.name()))
                .build();
        JwtEncoder encoder = NimbusJwtEncoder.withSecretKey(jwtSecretKey).algorithm(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        mockMvc.perform(get(SCHOOLS).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post(AuthController.LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private String newUsername() {
        return "it-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private UserAccount saveUser(String username, String password, Role role) {
        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setStatus(Status.ACTIVE);
        user.setRoles(Set.of(role));
        return userAccountRepository.save(user);
    }
}
