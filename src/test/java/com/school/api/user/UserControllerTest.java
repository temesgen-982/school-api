package com.school.api.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.school.api.common.domain.Role;
import com.school.api.common.domain.Status;
import com.school.api.common.exception.NotFoundException;
import com.school.api.common.web.ApiPaths;
import com.school.api.security.JwtConfig;
import com.school.api.security.SecurityConfig;
import com.school.api.user.dto.UserCreateRequest;
import com.school.api.user.dto.UserResponse;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = UserController.class)
@Import({SecurityConfig.class, JwtConfig.class})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    void createReturns201WithLocation() throws Exception {
        UserResponse response = responseOf(UUID.randomUUID());
        when(userService.create(any(UserCreateRequest.class))).thenReturn(response);

        mockMvc.perform(post(ApiPaths.V1 + "/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"kinder","password":"s3cret-pass","firstName":"Kin","lastName":"Derd","roles":["SCHOOL_ADMIN"]}
                                """)
                        .with(platformAdmin()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", ApiPaths.V1 + "/users/" + response.id()))
                .andExpect(jsonPath("$.id").value(response.id().toString()))
                .andExpect(jsonPath("$.username").value("kinder"))
                .andExpect(jsonPath("$.roles[0]").value("SCHOOL_ADMIN"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void createReturns400OnValidationErrors() throws Exception {
        mockMvc.perform(post(ApiPaths.V1 + "/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"","password":"short","roles":[]}
                                """)
                        .with(platformAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.username").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.roles").exists());
    }

    @Test
    void createReturns403ForNonAdmin() throws Exception {
        mockMvc.perform(post(ApiPaths.V1 + "/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"kinder","password":"s3cret-pass","roles":["SCHOOL_ADMIN"]}
                                """)
                        .with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void getReturnsUser() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.getById(id)).thenReturn(responseOf(id));

        mockMvc.perform(get(ApiPaths.V1 + "/users/{id}", id).with(platformAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("kinder"));
    }

    @Test
    void getReturns404WhenMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.getById(id)).thenThrow(new NotFoundException("User not found: " + id));

        mockMvc.perform(get(ApiPaths.V1 + "/users/{id}", id).with(platformAdmin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("User not found: " + id));
    }

    @Test
    void getReturns403ForSchoolScopedCaller() throws Exception {
        mockMvc.perform(get(ApiPaths.V1 + "/users/{id}", UUID.randomUUID())
                        .with(jwt().jwt(token -> token.claim("school_id", UUID.randomUUID().toString()))))
                .andExpect(status().isForbidden());
    }

    private static RequestPostProcessor platformAdmin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"));
    }

    private UserResponse responseOf(UUID id) {
        return new UserResponse(id, "kinder", "Kin", null, "Derd", null,
                Set.of(Role.SCHOOL_ADMIN), Status.ACTIVE, Instant.now());
    }
}
