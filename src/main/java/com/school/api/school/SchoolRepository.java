package com.school.api.school;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolRepository extends JpaRepository<School, UUID> {

    boolean existsByCode(String code);
}
