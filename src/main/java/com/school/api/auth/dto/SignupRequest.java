package com.school.api.auth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SignupRequest(
        @Valid
        @NotNull(message = "must not be blank")
        OwnerInput owner,

        @Valid
        @NotNull(message = "must not be blank")
        SchoolInput school,

        @Valid
        @NotEmpty(message = "must contain at least one branch")
        List<BranchInput> branches) {

    public record OwnerInput(
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
            String lastName) {
    }

    public record SchoolInput(
            @NotBlank(message = "must not be blank")
            @Size(max = 200)
            String name,

            @NotBlank(message = "must not be blank")
            @Size(max = 100)
            @Pattern(regexp = "[A-Za-z0-9]+(-[A-Za-z0-9]+)*",
                    message = "must contain only letters, digits and hyphens")
            String code,

            @Size(max = 50)
            String phone,

            @Email(message = "must be a valid email address")
            @Size(max = 255)
            String email) {
    }

    public record BranchInput(
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
}
