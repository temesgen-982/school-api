package com.school.api.school.dto;

import java.time.Instant;
import java.util.UUID;

public record OwnershipHistoryResponse(
        UUID schoolId,
        UUID ownerId,
        String ownerUsername,
        UUID previousOwnerId,
        String previousOwnerUsername,
        UUID transferredBy,
        String transferredByUsername,
        Instant transferredAt) {
}
