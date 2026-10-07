package com.school.api.school;

import com.school.api.common.domain.Status;
import com.school.api.common.exception.ConflictException;
import com.school.api.common.exception.NotFoundException;
import com.school.api.common.util.TextNormalizer;
import com.school.api.common.web.PageResponse;
import com.school.api.school.dto.SchoolCreateRequest;
import com.school.api.school.dto.SchoolPatchRequest;
import com.school.api.school.dto.SchoolResponse;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchoolService {

    private final SchoolRepository schoolRepository;

    public SchoolService(SchoolRepository schoolRepository) {
        this.schoolRepository = schoolRepository;
    }

    @Transactional
    public SchoolResponse create(SchoolCreateRequest request) {
        String code = TextNormalizer.normalizeCode(request.code());
        if (schoolRepository.existsByCode(code)) {
            throw new ConflictException("School code already exists: " + code);
        }
        School school = new School();
        school.setName(request.name().trim());
        school.setCode(code);
        school.setPhone(TextNormalizer.trimToNull(request.phone()));
        school.setEmail(TextNormalizer.trimToNull(request.email()));
        school.setStatus(Status.ACTIVE);
        schoolRepository.save(school);
        return SchoolResponse.from(school);
    }

    @Transactional(readOnly = true)
    public SchoolResponse getById(UUID id) {
        return SchoolResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<SchoolResponse> list(Pageable pageable) {
        return PageResponse.from(schoolRepository.findAll(pageable).map(SchoolResponse::from));
    }

    @Transactional
    public SchoolResponse patch(UUID id, SchoolPatchRequest request) {
        School school = find(id);
        if (request.name() != null) {
            school.setName(request.name().trim());
        }
        if (request.code() != null) {
            String code = TextNormalizer.normalizeCode(request.code());
            if (!code.equals(school.getCode()) && schoolRepository.existsByCode(code)) {
                throw new ConflictException("School code already exists: " + code);
            }
            school.setCode(code);
        }
        if (request.phone() != null) {
            school.setPhone(TextNormalizer.trimToNull(request.phone()));
        }
        if (request.email() != null) {
            school.setEmail(TextNormalizer.trimToNull(request.email()));
        }
        return SchoolResponse.from(school);
    }

    @Transactional
    public void deactivate(UUID id) {
        find(id).setStatus(Status.INACTIVE);
    }

    private School find(UUID id) {
        return schoolRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("School not found: " + id));
    }
}
