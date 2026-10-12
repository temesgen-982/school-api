package com.school.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.school.api.branch.BranchRepository;
import com.school.api.common.domain.Role;
import com.school.api.common.domain.Status;
import com.school.api.school.SchoolOwnershipHistoryRepository;
import com.school.api.school.SchoolRepository;
import com.school.api.user.UserAccountRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end check of public registration: one request atomically creates the owner,
 * school, and branches, the resulting account can log in immediately with the expected
 * roles and school scope, and conflicting or invalid payloads are rejected without
 * leaving data behind.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SignupIntegrationTest {

    private static final String SIGNUP = AuthController.SIGNUP;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private SchoolOwnershipHistoryRepository ownershipHistoryRepository;

    @Autowired
    private JwtDecoder jwtDecoder;

    private UUID createdUserId;
    private UUID createdSchoolId;

    @AfterEach
    void cleanUp() {
        if (createdSchoolId != null) {
            ownershipHistoryRepository
                    .findBySchoolIdOrderByTransferredAtDesc(createdSchoolId)
                    .forEach(ownershipHistoryRepository::delete);
            branchRepository.findBySchoolId(createdSchoolId, Pageable.unpaged())
                    .forEach(branchRepository::delete);
            // schools.owner_id and users.school_id reference each other, so unlink
            // the owner from the school before either side is deleted
            userAccountRepository.findById(createdUserId).ifPresent(owner -> {
                owner.setSchoolId(null);
                userAccountRepository.save(owner);
            });
            schoolRepository.findById(createdSchoolId).ifPresent(schoolRepository::delete);
            createdSchoolId = null;
        }
        if (createdUserId != null) {
            userAccountRepository.findById(createdUserId).ifPresent(userAccountRepository::delete);
            createdUserId = null;
        }
    }

    @Test
    void signupCreatesOwnerSchoolAndBranchesInOneGo() throws Exception {
        String username = unique("owner");
        String schoolCode = unique("school");

        MvcResult result = mockMvc.perform(post(SIGNUP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody(username, schoolCode)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.owner.username").value(username))
                .andExpect(jsonPath("$.school.code").value(schoolCode))
                .andExpect(jsonPath("$.owner.roles", org.hamcrest.Matchers.hasItems("OWNER", "SCHOOL_ADMIN")))
                .andReturn();
        String created = result.getResponse().getContentAsString();

        UUID ownerId = UUID.fromString(JsonPath.read(created, "$.owner.id"));
        UUID schoolId = UUID.fromString(JsonPath.read(created, "$.school.id"));
        createdUserId = ownerId;
        createdSchoolId = schoolId;

        assertThat(result.getResponse().getHeader("Location"))
                .isEqualTo("/api/v1/schools/" + schoolId);
        assertThat(JsonPath.<String>read(created, "$.owner.schoolId")).isEqualTo(schoolId.toString());
        assertThat(JsonPath.<String>read(created, "$.school.ownerId")).isEqualTo(ownerId.toString());
        assertThat(JsonPath.<List<String>>read(created, "$.branches[*].code"))
                .containsExactly("main-" + schoolCode, "annex-" + schoolCode);
        assertThat(JsonPath.<List<Boolean>>read(created, "$.branches[*].primary"))
                .containsExactly(true, false);

        var owner = userAccountRepository.findById(ownerId).orElseThrow();
        assertThat(owner.getSchoolId()).isEqualTo(schoolId);
        assertThat(owner.getRoles()).containsExactlyInAnyOrder(Role.OWNER, Role.SCHOOL_ADMIN);
        assertThat(owner.getPassword()).isNotEqualTo("s3cret-pass");

        var school = schoolRepository.findById(schoolId).orElseThrow();
        assertThat(school.getOwnerId()).isEqualTo(ownerId);
        assertThat(school.getStatus()).isEqualTo(Status.ACTIVE);

        var history = ownershipHistoryRepository.findBySchoolIdOrderByTransferredAtDesc(schoolId);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getOwnerId()).isEqualTo(ownerId);
        assertThat(history.get(0).getPreviousOwnerId()).isNull();

        String token = login(username, "s3cret-pass");
        Jwt jwt = jwtDecoder.decode(token);
        assertThat(jwt.getClaimAsStringList("roles")).containsExactlyInAnyOrder("OWNER", "SCHOOL_ADMIN");
        assertThat(jwt.getClaimAsString("school_id")).isEqualTo(schoolId.toString());
        assertThat(jwt.getClaimAsString("user_id")).isEqualTo(ownerId.toString());
    }

    @Test
    void signupHonorsExplicitPrimaryBranchFlag() throws Exception {
        String username = unique("owner");
        String schoolCode = unique("school");
        String body = """
                {
                  "owner": {"username": "%s", "password": "s3cret-pass"},
                  "school": {"name": "School", "code": "%s"},
                  "branches": [
                    {"name": "Satellite", "code": "satellite-%s"},
                    {"name": "HQ", "code": "hq-%s", "primary": true}
                  ]
                }
                """.formatted(username, schoolCode, schoolCode, schoolCode);

        String created = performSignup(body);
        createdUserId = UUID.fromString(JsonPath.read(created, "$.owner.id"));
        createdSchoolId = UUID.fromString(JsonPath.read(created, "$.school.id"));

        assertThat(JsonPath.<List<Boolean>>read(created, "$.branches[*].primary"))
                .containsExactly(false, true);
    }

    @Test
    void duplicateUsernameReturns409() throws Exception {
        String username = unique("owner");
        String first = performSignup(signupBody(username, unique("school")));
        createdUserId = UUID.fromString(JsonPath.read(first, "$.owner.id"));
        createdSchoolId = UUID.fromString(JsonPath.read(first, "$.school.id"));

        mockMvc.perform(post(SIGNUP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody(username, unique("school"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value("Username already exists: " + username));
    }

    @Test
    void duplicateSchoolCodeReturns409() throws Exception {
        String schoolCode = unique("school");
        String first = performSignup(signupBody(unique("owner"), schoolCode));
        createdUserId = UUID.fromString(JsonPath.read(first, "$.owner.id"));
        createdSchoolId = UUID.fromString(JsonPath.read(first, "$.school.id"));

        mockMvc.perform(post(SIGNUP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody(unique("owner"), schoolCode)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("School code already exists: " + schoolCode));
    }

    @Test
    void duplicateBranchCodesInPayloadReturn409() throws Exception {
        String schoolCode = unique("school");
        String body = """
                {
                  "owner": {"username": "%s", "password": "s3cret-pass"},
                  "school": {"name": "School", "code": "%s"},
                  "branches": [
                    {"name": "One", "code": "same-%s"},
                    {"name": "Two", "code": "SAME-%s"}
                  ]
                }
                """.formatted(unique("owner"), schoolCode, schoolCode, schoolCode);

        mockMvc.perform(post(SIGNUP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("Branch code already exists in this request: same-" + schoolCode));
    }

    @Test
    void multiplePrimaryBranchesReturn409() throws Exception {
        String schoolCode = unique("school");
        String body = """
                {
                  "owner": {"username": "%s", "password": "s3cret-pass"},
                  "school": {"name": "School", "code": "%s"},
                  "branches": [
                    {"name": "One", "code": "one-%s", "primary": true},
                    {"name": "Two", "code": "two-%s", "primary": true}
                  ]
                }
                """.formatted(unique("owner"), schoolCode, schoolCode, schoolCode);

        mockMvc.perform(post(SIGNUP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Only one branch can be marked as primary"));
    }

    @Test
    void invalidPayloadReturns400AndCreatesNothing() throws Exception {
        long usersBefore = userAccountRepository.count();
        long schoolsBefore = schoolRepository.count();

        mockMvc.perform(post(SIGNUP)
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

        assertThat(userAccountRepository.count()).isEqualTo(usersBefore);
        assertThat(schoolRepository.count()).isEqualTo(schoolsBefore);
    }

    private String performSignup(String body) throws Exception {
        return mockMvc.perform(post(SIGNUP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
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

    private static String signupBody(String username, String schoolCode) {
        return """
                {
                  "owner": {"username": "%s", "password": "s3cret-pass", "firstName": "First", "lastName": "Last"},
                  "school": {"name": "School", "code": "%s"},
                  "branches": [
                    {"name": "Main", "code": "main-%s", "primary": true},
                    {"name": "Annex", "code": "annex-%s"}
                  ]
                }
                """.formatted(username, schoolCode, schoolCode, schoolCode);
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
