package com.school.api.school;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.school.api.auth.AuthController;
import com.school.api.common.domain.Role;
import com.school.api.common.domain.Status;
import com.school.api.common.web.ApiPaths;
import com.school.api.user.UserAccount;
import com.school.api.user.UserAccountRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end ownership lifecycle: a platform admin creates a school and a user, hands
 * ownership over (granted OWNER role + school membership + history row), the new owner
 * can then manage their own school but reaches nothing else, and a second transfer
 * moves ownership on and strips the OWNER role from the previous holder.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SchoolOwnershipIntegrationTest {

    private static final String SCHOOLS = ApiPaths.V1 + "/schools";
    private static final String USERS = ApiPaths.V1 + "/users";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private SchoolOwnershipHistoryRepository ownershipHistoryRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final List<UUID> createdUserIds = new ArrayList<>();
    private final List<UUID> createdSchoolIds = new ArrayList<>();
    private final String runId = UUID.randomUUID().toString().substring(0, 8);
    private int schoolCounter;

    @AfterEach
    void cleanUp() {
        for (UUID schoolId : createdSchoolIds) {
            ownershipHistoryRepository.findBySchoolIdOrderByTransferredAtDesc(schoolId)
                    .forEach(ownershipHistoryRepository::delete);
        }
        // schools.owner_id and users.school_id reference each other, so break the
        // cycle before deleting either side
        createdSchoolIds.forEach(id -> schoolRepository.findById(id).ifPresent(school -> {
            school.setOwnerId(null);
            schoolRepository.save(school);
        }));
        createdUserIds.forEach(id -> userAccountRepository.findById(id).ifPresent(userAccountRepository::delete));
        createdUserIds.clear();
        createdSchoolIds.forEach(id -> schoolRepository.findById(id).ifPresent(schoolRepository::delete));
        createdSchoolIds.clear();
    }

    @Test
    void ownershipCanBeGrantedManagedAndTransferred() throws Exception {
        String adminToken = platformAdminToken();
        UUID schoolId = createSchool(adminToken, "Ownership High");
        CreatedUser firstOwner = createUser(adminToken, "owner-one");
        CreatedUser secondOwner = createUser(adminToken, "owner-two");

        // Platform admin hands the school to its first owner
        mockMvc.perform(put(SCHOOLS + "/" + schoolId + "/owner")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s"}
                                """.formatted(firstOwner.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").value(firstOwner.id().toString()));

        School school = schoolRepository.findById(schoolId).orElseThrow();
        assertThat(school.getOwnerId()).isEqualTo(firstOwner.id());
        UserAccount firstOwnerAccount = userAccountRepository.findById(firstOwner.id()).orElseThrow();
        assertThat(firstOwnerAccount.getSchoolId()).isEqualTo(schoolId);
        assertThat(firstOwnerAccount.getRoles()).contains(Role.OWNER);
        List<SchoolOwnershipHistory> history =
                ownershipHistoryRepository.findBySchoolIdOrderByTransferredAtDesc(schoolId);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getOwnerId()).isEqualTo(firstOwner.id());
        assertThat(history.get(0).getPreviousOwnerId()).isNull();

        // The history is visible over the API
        mockMvc.perform(get(SCHOOLS + "/" + schoolId + "/owner/history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ownerUsername").value(firstOwner.username()))
                .andExpect(jsonPath("$[0].transferredByUsername").isNotEmpty());

        // The new owner logs in and is scoped to their school only
        String ownerToken = login(firstOwner.username(), "s3cret-pass");
        mockMvc.perform(get(SCHOOLS + "/" + schoolId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk());
        mockMvc.perform(get(SCHOOLS)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isForbidden());

        // ... and may manage it
        mockMvc.perform(patch(SCHOOLS + "/" + schoolId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ownership High Renamed"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ownership High Renamed"));

        // The owner transfers ownership on to the next holder
        mockMvc.perform(put(SCHOOLS + "/" + schoolId + "/owner")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s"}
                                """.formatted(secondOwner.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").value(secondOwner.id().toString()));

        school = schoolRepository.findById(schoolId).orElseThrow();
        assertThat(school.getOwnerId()).isEqualTo(secondOwner.id());
        firstOwnerAccount = userAccountRepository.findById(firstOwner.id()).orElseThrow();
        assertThat(firstOwnerAccount.getRoles()).doesNotContain(Role.OWNER);
        assertThat(firstOwnerAccount.getSchoolId()).isEqualTo(schoolId);
        UserAccount secondOwnerAccount = userAccountRepository.findById(secondOwner.id()).orElseThrow();
        assertThat(secondOwnerAccount.getRoles()).contains(Role.OWNER);

        history = ownershipHistoryRepository.findBySchoolIdOrderByTransferredAtDesc(schoolId);
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getOwnerId()).isEqualTo(secondOwner.id());
        assertThat(history.get(0).getPreviousOwnerId()).isEqualTo(firstOwner.id());

        // The displaced owner keeps read access to the school but can no longer manage it
        ownerToken = login(firstOwner.username(), "s3cret-pass");
        mockMvc.perform(get(SCHOOLS + "/" + schoolId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk());
        mockMvc.perform(patch(SCHOOLS + "/" + schoolId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Should Not Stick"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void transferRejectsUserWhoAlreadyOwnsAnotherSchool() throws Exception {
        String adminToken = platformAdminToken();
        UUID firstSchoolId = createSchool(adminToken, "School Alpha");
        UUID secondSchoolId = createSchool(adminToken, "School Beta");
        CreatedUser sharedOwner = createUser(adminToken, "owner-shared");

        mockMvc.perform(put(SCHOOLS + "/" + firstSchoolId + "/owner")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s"}
                                """.formatted(sharedOwner.id())))
                .andExpect(status().isOk());

        mockMvc.perform(put(SCHOOLS + "/" + secondSchoolId + "/owner")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s"}
                                """.formatted(sharedOwner.id())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"));
    }

    private String platformAdminToken() throws Exception {
        String username = "it-" + runId + "-" + (++schoolCounter);
        UserAccount admin = new UserAccount();
        admin.setUsername(username);
        admin.setPassword(passwordEncoder.encode("s3cret-pass"));
        admin.setStatus(Status.ACTIVE);
        admin.setRoles(Set.of(Role.PLATFORM_ADMIN));
        createdUserIds.add(userAccountRepository.save(admin).getId());
        return login(username, "s3cret-pass");
    }

    private UUID createSchool(String token, String name) throws Exception {
        String code = "own-" + runId + "-" + (++schoolCounter);
        String body = mockMvc.perform(post(SCHOOLS)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","code":"%s"}
                                """.formatted(name, code)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(body, "$.id"));
        createdSchoolIds.add(id);
        return id;
    }

    private CreatedUser createUser(String token, String prefix) throws Exception {
        String username = prefix + "-" + runId;
        String body = mockMvc.perform(post(USERS)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"s3cret-pass","firstName":"Owner","lastName":"One","roles":["SCHOOL_ADMIN"]}
                                """.formatted(username)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(body, "$.id"));
        createdUserIds.add(id);
        return new CreatedUser(id, username);
    }

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post(AuthController.LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private record CreatedUser(UUID id, String username) {
    }
}
