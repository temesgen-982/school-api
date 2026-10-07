package com.school.api.branch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BranchCreateRequest(
        @NotBlank(message = "must not be blank")
        @Size(max = 200)
        String name,

        @NotBlank(message = "must not be blank")
        @Size(max = 100)
        @Pattern(regexp = "[A-Za-z0-9]+(-[A-Za-z0-9]+)*",
                message = "must contain only letters, digits and hyphens")
        String code,

        Boolean primary,

        @Size(max = 255)
        String addressLine,

        @Size(max = 100)
        String city,

        @Size(max = 100)
        String region,

        @Size(max = 50)
        String phone) {
}
