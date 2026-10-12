package com.school.api.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.school.api.auth.dto.LoginRequest;
import com.school.api.auth.dto.LoginResponse;
import com.school.api.auth.dto.SignupRequest;
import com.school.api.auth.dto.SignupResponse;
import com.school.api.branch.dto.BranchResponse;
import com.school.api.common.domain.Role;
import com.school.api.common.domain.Status;
import com.school.api.common.exception.ConflictException;
import com.school.api.common.exception.UnauthorizedException;
import com.school.api.school.dto.SchoolResponse;
import com.school.api.security.JwtConfig;
import com.school.api.security.SecurityConfig;
import com.school.api.user.dto.UserResponse;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, JwtConfig.class})
class AuthControllerTest {

    private static final UUID SCHOOL_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID USER_ID = UUID.fromString("66666666-7777-8888-9999-000000000000");
    private static final UUID BRANCH_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private SignupService signupService;

    @Test
    void loginReturnsToken() throws Exception {
        Instant expiresAt = Instant.now().plusSeconds(3600);
        when(authService.login(any(LoginRequest.class)))
                .thenReturn(new LoginResponse("test-token", "Bearer", expiresAt));

        mockMvc.perform(post(AuthController.LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"secret"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("test-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());
    }

    @Test
    void loginReturns400OnBlankCredentials() throws Exception {
        mockMvc.perform(post(AuthController.LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"","password":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.username").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void loginReturns401OnBadCredentials() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new UnauthorizedException("Invalid username or password"));

        mockMvc.perform(post(AuthController.LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));
    }

    @Test
    void signupReturns201WithCreatedResourcesAndLocation() throws Exception {
        when(signupService.signup(any(SignupRequest.class))).thenReturn(sampleSignupResponse());

        mockMvc.perform(post(AuthController.SIGNUP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sampleSignupBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.owner.username").value("owner-1"))
                .andExpect(jsonPath("$.school.code").value("school-1"))
                .andExpect(jsonPath("$.branches[0].code").value("main"))
                .andExpect(jsonPath("$.branches[1].code").value("annex"))
                .andExpect(header().string("Location", "/api/v1/schools/" + SCHOOL_ID));
    }

    @Test
    void signupReturns400OnInvalidPayload() throws Exception {
        mockMvc.perform(post(AuthController.SIGNUP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner":{"username":"","password":"short"},"school":null,"branches":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors['owner.username']").exists())
                .andExpect(jsonPath("$.errors['owner.password']").exists())
                .andExpect(jsonPath("$.errors.school").exists())
                .andExpect(jsonPath("$.errors.branches").exists());
    }

    @Test
    void signupReturns409OnConflict() throws Exception {
        when(signupService.signup(any(SignupRequest.class)))
                .thenThrow(new ConflictException("Username already exists: owner-1"));

        mockMvc.perform(post(AuthController.SIGNUP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sampleSignupBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value("Username already exists: owner-1"));
    }

    private static SignupResponse sampleSignupResponse() {
        Instant createdAt = Instant.now();
        UserResponse owner = new UserResponse(
                USER_ID, "owner-1", "First", null, "Last", SCHOOL_ID,
                Set.of(Role.OWNER, Role.SCHOOL_ADMIN), Status.ACTIVE, createdAt);
        SchoolResponse school = new SchoolResponse(
                SCHOOL_ID, "School One", "school-1", Status.ACTIVE, null, null,
                USER_ID, createdAt, createdAt);
        BranchResponse branch = new BranchResponse(
                BRANCH_ID, SCHOOL_ID, "Main", "main", true, Status.ACTIVE,
                null, null, null, null, createdAt, createdAt);
        BranchResponse annex = new BranchResponse(
                BRANCH_ID, SCHOOL_ID, "Annex", "annex", false, Status.ACTIVE,
                null, null, null, null, createdAt, createdAt);
        return new SignupResponse(owner, school, List.of(branch, annex));
    }

    private static String sampleSignupBody() {
        return """
                {
                  "owner": {"username": "owner-1", "password": "s3cret-pass", "firstName": "First", "lastName": "Last"},
                  "school": {"name": "School One", "code": "school-1"},
                  "branches": [
                    {"name": "Main", "code": "main", "primary": true},
                    {"name": "Annex", "code": "annex"}
                  ]
                }
                """;
    }
}
