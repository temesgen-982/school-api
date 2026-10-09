package com.school.api.user.dto;

import com.school.api.common.domain.Role;
import com.school.api.common.domain.Status;
import com.school.api.user.UserAccount;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String firstName,
        String middleName,
        String lastName,
        UUID schoolId,
        Set<Role> roles,
        Status status,
        Instant createdAt) {

    public static UserResponse from(UserAccount user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getFirstName(),
                user.getMiddleName(),
                user.getLastName(),
                user.getSchoolId(),
                Set.copyOf(user.getRoles()),
                user.getStatus(),
                user.getCreatedAt());
    }
}
