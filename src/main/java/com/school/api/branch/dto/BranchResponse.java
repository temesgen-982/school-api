package com.school.api.branch.dto;

import com.school.api.branch.Branch;
import com.school.api.common.domain.Status;
import java.time.Instant;
import java.util.UUID;

public record BranchResponse(
        UUID id,
        UUID schoolId,
        String name,
        String code,
        boolean primary,
        Status status,
        String addressLine,
        String city,
        String region,
        String phone,
        Instant createdAt,
        Instant updatedAt) {

    public static BranchResponse from(Branch branch) {
        return new BranchResponse(
                branch.getId(),
                branch.getSchoolId(),
                branch.getName(),
                branch.getCode(),
                branch.isPrimary(),
                branch.getStatus(),
                branch.getAddressLine(),
                branch.getCity(),
                branch.getRegion(),
                branch.getPhone(),
                branch.getCreatedAt(),
                branch.getUpdatedAt());
    }
}
