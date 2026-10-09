package com.school.api.school;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolOwnershipHistoryRepository extends JpaRepository<SchoolOwnershipHistory, UUID> {

    List<SchoolOwnershipHistory> findBySchoolIdOrderByTransferredAtDesc(UUID schoolId);
}
