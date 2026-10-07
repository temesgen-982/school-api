package com.school.api.branch;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.school.api.branch.dto.BranchCreateRequest;
import com.school.api.branch.dto.BranchResponse;
import com.school.api.common.domain.Status;
import com.school.api.common.exception.NotFoundException;
import com.school.api.common.web.ApiPaths;
import com.school.api.common.web.PageResponse;
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

@WebMvcTest(controllers = BranchController.class)
@Import({SecurityConfig.class, JwtConfig.class})
class BranchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BranchService branchService;

    @Test
    void createReturns201WithLocation() throws Exception {
        UUID schoolId = UUID.randomUUID();
        String branches = ApiPaths.V1 + "/schools/" + schoolId + "/branches";
        BranchResponse response = responseOf(UUID.randomUUID(), schoolId);
        when(branchService.create(eq(schoolId), any(BranchCreateRequest.class))).thenReturn(response);

        mockMvc.perform(post(branches)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Main Campus","code":"main","primary":true,"city":"Addis Ababa"}
                                """)
                        .with(platformAdmin()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", branches + "/" + response.id()))
                .andExpect(jsonPath("$.id").value(response.id().toString()))
                .andExpect(jsonPath("$.schoolId").value(schoolId.toString()))
                .andExpect(jsonPath("$.primary").value(true));
    }

    @Test
    void createReturns400OnValidationErrors() throws Exception {
        UUID schoolId = UUID.randomUUID();

        mockMvc.perform(post(ApiPaths.V1 + "/schools/{schoolId}/branches", schoolId)
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
    void getReturns404WhenBranchNotInSchool() throws Exception {
        UUID schoolId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(branchService.getById(schoolId, id))
                .thenThrow(new NotFoundException("Branch not found: " + id));

        mockMvc.perform(get(ApiPaths.V1 + "/schools/{schoolId}/branches/{id}", schoolId, id).with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Branch not found: " + id));
    }

    @Test
    void getReturns401WithoutToken() throws Exception {
        UUID schoolId = UUID.randomUUID();

        mockMvc.perform(get(ApiPaths.V1 + "/schools/{schoolId}/branches", schoolId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listReturnsPageScopedToSchool() throws Exception {
        UUID schoolId = UUID.randomUUID();
        when(branchService.list(eq(schoolId), any(Pageable.class)))
                .thenReturn(PageResponse.from(new PageImpl<>(List.of(responseOf(UUID.randomUUID(), schoolId)))));

        mockMvc.perform(get(ApiPaths.V1 + "/schools/{schoolId}/branches", schoolId).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Main Campus"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void deleteReturns204AndDeactivates() throws Exception {
        UUID schoolId = UUID.randomUUID();
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete(ApiPaths.V1 + "/schools/{schoolId}/branches/{id}", schoolId, id)
                        .with(platformAdmin()))
                .andExpect(status().isNoContent());

        verify(branchService).deactivate(schoolId, id);
    }

    private static RequestPostProcessor platformAdmin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"));
    }

    private BranchResponse responseOf(UUID id, UUID schoolId) {
        return new BranchResponse(id, schoolId, "Main Campus", "main", true, Status.ACTIVE,
                null, "Addis Ababa", null, null, Instant.now(), Instant.now());
    }
}
