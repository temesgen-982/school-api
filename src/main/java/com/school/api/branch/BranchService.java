package com.school.api.branch;

import com.school.api.branch.dto.BranchCreateRequest;
import com.school.api.branch.dto.BranchPatchRequest;
import com.school.api.branch.dto.BranchResponse;
import com.school.api.common.domain.Status;
import com.school.api.common.exception.ConflictException;
import com.school.api.common.exception.NotFoundException;
import com.school.api.common.util.TextNormalizer;
import com.school.api.common.web.PageResponse;
import com.school.api.school.School;
import com.school.api.school.SchoolRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BranchService {

    private final BranchRepository branchRepository;
    private final SchoolRepository schoolRepository;

    public BranchService(BranchRepository branchRepository, SchoolRepository schoolRepository) {
        this.branchRepository = branchRepository;
        this.schoolRepository = schoolRepository;
    }

    @Transactional
    public BranchResponse create(UUID schoolId, BranchCreateRequest request) {
        schoolRepository.findById(schoolId)
                .orElseThrow(() -> new NotFoundException("School not found: " + schoolId));
        String code = TextNormalizer.normalizeCode(request.code());
        if (branchRepository.existsBySchoolIdAndCode(schoolId, code)) {
            throw new ConflictException("Branch code already exists for this school: " + code);
        }
        Branch branch = new Branch();
        branch.setSchoolId(schoolId);
        branch.setName(request.name().trim());
        branch.setCode(code);
        branch.setAddressLine(TextNormalizer.trimToNull(request.addressLine()));
        branch.setCity(TextNormalizer.trimToNull(request.city()));
        branch.setRegion(TextNormalizer.trimToNull(request.region()));
        branch.setPhone(TextNormalizer.trimToNull(request.phone()));
        branch.setStatus(Status.ACTIVE);
        boolean primary = Boolean.TRUE.equals(request.primary());
        if (primary) {
            demoteOtherPrimaries(schoolId, null);
        }
        branch.setPrimary(primary);
        branchRepository.save(branch);
        return BranchResponse.from(branch);
    }

    @Transactional(readOnly = true)
    public BranchResponse getById(UUID schoolId, UUID id) {
        return BranchResponse.from(find(schoolId, id));
    }

    @Transactional(readOnly = true)
    public PageResponse<BranchResponse> list(UUID schoolId, Pageable pageable) {
        schoolRepository.findById(schoolId)
                .orElseThrow(() -> new NotFoundException("School not found: " + schoolId));
        return PageResponse.from(branchRepository.findBySchoolId(schoolId, pageable).map(BranchResponse::from));
    }

    @Transactional
    public BranchResponse patch(UUID schoolId, UUID id, BranchPatchRequest request) {
        Branch branch = find(schoolId, id);
        if (request.name() != null) {
            branch.setName(request.name().trim());
        }
        if (request.code() != null) {
            String code = TextNormalizer.normalizeCode(request.code());
            if (!code.equals(branch.getCode())
                    && branchRepository.existsBySchoolIdAndCodeAndIdNot(schoolId, code, id)) {
                throw new ConflictException("Branch code already exists for this school: " + code);
            }
            branch.setCode(code);
        }
        if (request.primary() != null) {
            if (request.primary()) {
                demoteOtherPrimaries(schoolId, id);
                branch.setPrimary(true);
            } else {
                branch.setPrimary(false);
            }
        }
        if (request.addressLine() != null) {
            branch.setAddressLine(TextNormalizer.trimToNull(request.addressLine()));
        }
        if (request.city() != null) {
            branch.setCity(TextNormalizer.trimToNull(request.city()));
        }
        if (request.region() != null) {
            branch.setRegion(TextNormalizer.trimToNull(request.region()));
        }
        if (request.phone() != null) {
            branch.setPhone(TextNormalizer.trimToNull(request.phone()));
        }
        return BranchResponse.from(branch);
    }

    @Transactional
    public void deactivate(UUID schoolId, UUID id, UUID deletedBy) {
        Branch branch = find(schoolId, id);
        branch.setStatus(Status.INACTIVE);
        branch.setDeletedAt(Instant.now());
        branch.setDeletedBy(deletedBy);
    }

    private Branch find(UUID schoolId, UUID id) {
        return branchRepository.findByIdAndSchoolId(id, schoolId)
                .orElseThrow(() -> new NotFoundException("Branch not found: " + id));
    }

    private void demoteOtherPrimaries(UUID schoolId, UUID exceptId) {
        branchRepository.findBySchoolIdAndPrimaryTrue(schoolId).stream()
                .filter(existing -> exceptId == null || !exceptId.equals(existing.getId()))
                .forEach(existing -> existing.setPrimary(false));
    }
}
