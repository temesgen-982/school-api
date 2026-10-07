package com.school.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "must not be blank")
        String username,

        @NotBlank(message = "must not be blank")
        String password) {
}
