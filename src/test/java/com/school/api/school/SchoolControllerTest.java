package com.school.api.school;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.school.api.common.domain.Status;
import com.school.api.common.exception.NotFoundException;
import com.school.api.common.web.PageResponse;
import com.school.api.school.dto.SchoolCreateRequest;
import com.school.api.school.dto.SchoolPatchRequest;
import com.school.api.school.dto.SchoolResponse;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SchoolController.class)
@Import(SecurityConfig.class)
class SchoolControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SchoolService schoolService;

    @Test
    void createReturns201WithLocation() throws Exception {
        SchoolResponse response = responseOf(UUID.randomUUID());
        when(schoolService.create(any(SchoolCreateRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/schools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Bright Future","code":"bright-future","phone":"+251911000000","email":"info@bright.edu"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/schools/" + response.id()))
                .andExpect(jsonPath("$.id").value(response.id().toString()))
                .andExpect(jsonPath("$.name").value("Bright Future"))
                .andExpect(jsonPath("$.code").value("bright-future"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void createReturns400OnValidationErrors() throws Exception {
        mockMvc.perform(post("/api/schools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","code":"not a code!"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.code").exists());
    }

    @Test
    void createReturns400OnMalformedJson() throws Exception {
        mockMvc.perform(post("/api/schools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"));
    }

    @Test
    void getReturns404WhenMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(schoolService.getById(id)).thenThrow(new NotFoundException("School not found: " + id));

        mockMvc.perform(get("/api/schools/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.detail").value("School not found: " + id));
    }

    @Test
    void getReturns400OnInvalidId() throws Exception {
        mockMvc.perform(get("/api/schools/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid parameter"));
    }

    @Test
    void listReturnsPage() throws Exception {
        when(schoolService.list(any(Pageable.class)))
                .thenReturn(PageResponse.from(new PageImpl<>(List.of(responseOf(UUID.randomUUID())))));

        mockMvc.perform(get("/api/schools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Bright Future"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void patchReturnsUpdatedSchool() throws Exception {
        UUID id = UUID.randomUUID();
        SchoolResponse updated = new SchoolResponse(id, "Renamed", "bright-future", Status.ACTIVE,
                null, null, Instant.now(), Instant.now());
        when(schoolService.patch(eq(id), any(SchoolPatchRequest.class))).thenReturn(updated);

        mockMvc.perform(patch("/api/schools/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Renamed"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"));
    }

    @Test
    void deleteReturns204AndDeactivates() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/schools/{id}", id))
                .andExpect(status().isNoContent());

        verify(schoolService).deactivate(id);
    }

    private SchoolResponse responseOf(UUID id) {
        return new SchoolResponse(id, "Bright Future", "bright-future", Status.ACTIVE,
                "+251911000000", "info@bright.edu", Instant.now(), Instant.now());
    }
}
