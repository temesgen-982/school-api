package com.school.api.school;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.school.api.common.domain.Status;
import com.school.api.common.exception.NotFoundException;
import com.school.api.common.web.ApiPaths;
import com.school.api.common.web.PageResponse;
import com.school.api.school.dto.OwnershipHistoryResponse;
import com.school.api.school.dto.SchoolCreateRequest;
import com.school.api.school.dto.SchoolPatchRequest;
import com.school.api.school.dto.SchoolResponse;
import com.school.api.school.dto.TransferOwnerRequest;
import com.school.api.security.JwtConfig;
import com.school.api.security.SecurityConfig;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = SchoolController.class)
@Import({SecurityConfig.class, JwtConfig.class})
class SchoolControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SchoolService schoolService;

    @Test
    void createReturns201WithLocation() throws Exception {
        SchoolResponse response = responseOf(UUID.randomUUID());
        when(schoolService.create(any(SchoolCreateRequest.class))).thenReturn(response);

        mockMvc.perform(post(ApiPaths.V1 + "/schools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Bright Future","code":"bright-future","phone":"+251911000000","email":"info@bright.edu"}
                                """)
                        .with(platformAdmin()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", ApiPaths.V1 + "/schools/" + response.id()))
                .andExpect(jsonPath("$.id").value(response.id().toString()))
                .andExpect(jsonPath("$.name").value("Bright Future"))
                .andExpect(jsonPath("$.code").value("bright-future"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void createReturns400OnValidationErrors() throws Exception {
        mockMvc.perform(post(ApiPaths.V1 + "/schools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","code":"not a code!"}
                                """)
                        .with(platformAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.code").exists());
    }

    @Test
    void createReturns400OnMalformedJson() throws Exception {
        mockMvc.perform(post(ApiPaths.V1 + "/schools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json")
                        .with(platformAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"));
    }

    @Test
    void createReturns403ForNonAdmin() throws Exception {
        mockMvc.perform(post(ApiPaths.V1 + "/schools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Bright Future","code":"bright-future"}
                                """)
                        .with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void getReturns404WhenMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(schoolService.getById(id)).thenThrow(new NotFoundException("School not found: " + id));

        mockMvc.perform(get(ApiPaths.V1 + "/schools/{id}", id).with(schoolScoped(id)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.detail").value("School not found: " + id));
    }

    @Test
    void getReturns400OnInvalidId() throws Exception {
        mockMvc.perform(get(ApiPaths.V1 + "/schools/not-a-uuid").with(schoolScopedString("not-a-uuid")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid parameter"));
    }

    @Test
    void getReturns401WithoutToken() throws Exception {
        mockMvc.perform(get(ApiPaths.V1 + "/schools"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getReturns403WhenCallerIsScopedToAnotherSchool() throws Exception {
        mockMvc.perform(get(ApiPaths.V1 + "/schools/{id}", UUID.randomUUID())
                        .with(schoolScoped(UUID.randomUUID())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @Test
    void listReturns403ForSchoolScopedCaller() throws Exception {
        mockMvc.perform(get(ApiPaths.V1 + "/schools").with(schoolScoped(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void listReturnsPage() throws Exception {
        when(schoolService.list(any(Pageable.class)))
                .thenReturn(PageResponse.from(new PageImpl<>(List.of(responseOf(UUID.randomUUID())))));

        mockMvc.perform(get(ApiPaths.V1 + "/schools").with(platformAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Bright Future"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void patchReturnsUpdatedSchool() throws Exception {
        UUID id = UUID.randomUUID();
        SchoolResponse updated = new SchoolResponse(id, "Renamed", "bright-future", Status.ACTIVE,
                null, null, null, Instant.now(), Instant.now());
        when(schoolService.patch(eq(id), any(SchoolPatchRequest.class))).thenReturn(updated);

        mockMvc.perform(patch(ApiPaths.V1 + "/schools/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Renamed"}
                                """)
                        .with(platformAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"));
    }

    @Test
    void patchReturnsUpdatedSchoolForOwnerOfThatSchool() throws Exception {
        UUID id = UUID.randomUUID();
        SchoolResponse updated = new SchoolResponse(id, "Renamed", "bright-future", Status.ACTIVE,
                null, null, UUID.randomUUID(), Instant.now(), Instant.now());
        when(schoolService.patch(eq(id), any(SchoolPatchRequest.class))).thenReturn(updated);

        mockMvc.perform(patch(ApiPaths.V1 + "/schools/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Renamed"}
                                """)
                        .with(ownerScoped(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"));
    }

    @Test
    void patchReturns403ForOwnerOfAnotherSchool() throws Exception {
        mockMvc.perform(patch(ApiPaths.V1 + "/schools/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Renamed"}
                                """)
                        .with(ownerScoped(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteReturns204AndDeactivates() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete(ApiPaths.V1 + "/schools/{id}", id).with(platformAdmin()))
                .andExpect(status().isNoContent());

        verify(schoolService).deactivate(id, null);
    }

    @Test
    void transferOwnerReturnsUpdatedSchool() throws Exception {
        UUID id = UUID.randomUUID();
        UUID newOwnerId = UUID.randomUUID();
        when(schoolService.transferOwner(eq(id), any(TransferOwnerRequest.class), eq(null), eq(true)))
                .thenReturn(responseOf(id, newOwnerId));

        mockMvc.perform(put(ApiPaths.V1 + "/schools/{id}/owner", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s"}
                                """.formatted(newOwnerId))
                        .with(platformAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").value(newOwnerId.toString()));
    }

    @Test
    void transferOwnerReturns403WithoutOwnerRole() throws Exception {
        mockMvc.perform(put(ApiPaths.V1 + "/schools/{id}/owner", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s"}
                                """.formatted(UUID.randomUUID()))
                        .with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void transferOwnerReturns403WhenScopedToAnotherSchool() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(put(ApiPaths.V1 + "/schools/{id}/owner", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s"}
                                """.formatted(UUID.randomUUID()))
                        .with(ownerScoped(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void transferOwnerReturns400WhenUserIdMissing() throws Exception {
        mockMvc.perform(put(ApiPaths.V1 + "/schools/{id}/owner", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(platformAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.userId").exists());
    }

    @Test
    void ownerHistoryReturnsTransfers() throws Exception {
        UUID id = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(schoolService.ownerHistory(id)).thenReturn(List.of(
                new OwnershipHistoryResponse(id, ownerId, "owner", null, null, null, "admin", Instant.now())));

        mockMvc.perform(get(ApiPaths.V1 + "/schools/{id}/owner/history", id).with(platformAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ownerUsername").value("owner"))
                .andExpect(jsonPath("$[0].previousOwnerUsername").doesNotExist());
    }

    private static RequestPostProcessor platformAdmin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"));
    }

    private static RequestPostProcessor schoolScoped(UUID schoolId) {
        return schoolScopedString(schoolId.toString());
    }

    private static RequestPostProcessor schoolScopedString(String schoolId) {
        return jwt().jwt(token -> token.claim("school_id", schoolId));
    }

    private static RequestPostProcessor ownerScoped(UUID schoolId) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_OWNER"))
                .jwt(token -> token.claim("school_id", schoolId.toString()));
    }

    private SchoolResponse responseOf(UUID id) {
        return new SchoolResponse(id, "Bright Future", "bright-future", Status.ACTIVE,
                "+251911000000", "info@bright.edu", null, Instant.now(), Instant.now());
    }

    private SchoolResponse responseOf(UUID id, UUID ownerId) {
        return new SchoolResponse(id, "Bright Future", "bright-future", Status.ACTIVE,
                "+251911000000", "info@bright.edu", ownerId, Instant.now(), Instant.now());
    }
}
