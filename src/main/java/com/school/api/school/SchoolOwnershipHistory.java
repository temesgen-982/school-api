package com.school.api.school;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only record of a school ownership transfer: the owner being replaced (nullable
 * for the first assignment), the new owner, who executed the transfer, and when.
 */
@Entity
@Table(name = "school_ownership_history")
public class SchoolOwnershipHistory {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(name = "previous_owner_id")
    private UUID previousOwnerId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "transferred_by")
    private UUID transferredBy;

    @Column(name = "transferred_at", nullable = false)
    private Instant transferredAt;

    protected SchoolOwnershipHistory() {
    }

    public SchoolOwnershipHistory(UUID schoolId, UUID previousOwnerId, UUID ownerId, UUID transferredBy) {
        this.schoolId = schoolId;
        this.previousOwnerId = previousOwnerId;
        this.ownerId = ownerId;
        this.transferredBy = transferredBy;
        this.transferredAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (transferredAt == null) {
            transferredAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public UUID getPreviousOwnerId() {
        return previousOwnerId;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getTransferredBy() {
        return transferredBy;
    }

    public Instant getTransferredAt() {
        return transferredAt;
    }
}
