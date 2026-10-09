package com.school.api.school.dto;

import com.school.api.common.domain.Status;
import com.school.api.school.School;
import java.time.Instant;
import java.util.UUID;

public record SchoolResponse(
        UUID id,
        String name,
        String code,
        Status status,
        String phone,
        String email,
        UUID ownerId,
        Instant createdAt,
        Instant updatedAt) {

    public static SchoolResponse from(School school) {
        return new SchoolResponse(
                school.getId(),
                school.getName(),
                school.getCode(),
                school.getStatus(),
                school.getPhone(),
                school.getEmail(),
                school.getOwnerId(),
                school.getCreatedAt(),
                school.getUpdatedAt());
    }
}
