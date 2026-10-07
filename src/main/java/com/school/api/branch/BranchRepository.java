package com.school.api.branch;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<Branch, UUID> {

    Page<Branch> findBySchoolId(UUID schoolId, Pageable pageable);

    Optional<Branch> findByIdAndSchoolId(UUID id, UUID schoolId);

    boolean existsBySchoolIdAndCode(UUID schoolId, String code);

    boolean existsBySchoolIdAndCodeAndIdNot(UUID schoolId, String code, UUID id);

    List<Branch> findBySchoolIdAndPrimaryTrue(UUID schoolId);
}
