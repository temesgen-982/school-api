package com.school.api.school.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record TransferOwnerRequest(
        @NotNull(message = "must not be null")
        UUID userId) {
}
