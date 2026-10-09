package com.school.api.user.dto;

import com.school.api.common.domain.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

public record UserCreateRequest(
        @NotBlank(message = "must not be blank")
        @Size(max = 100)
        String username,

        @NotBlank(message = "must not be blank")
        @Size(min = 8, max = 100)
        String password,

        @Size(max = 100)
        String firstName,

        @Size(max = 100)
        String middleName,

        @Size(max = 100)
        String lastName,

        @NotEmpty(message = "must contain at least one role")
        Set<Role> roles,

        UUID schoolId) {
}
