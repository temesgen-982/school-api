package com.school.api.school.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SchoolPatchRequest(
        @Size(min = 1, max = 200)
        String name,

        @Size(min = 1, max = 100)
        @Pattern(regexp = "[A-Za-z0-9]+(-[A-Za-z0-9]+)*",
                message = "must contain only letters, digits and hyphens")
        String code,

        @Size(max = 50)
        String phone,

        @Email(message = "must be a valid email address")
        @Size(max = 255)
        String email) {
}
