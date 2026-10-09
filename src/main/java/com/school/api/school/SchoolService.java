package com.school.api.school;

import com.school.api.common.domain.Role;
import com.school.api.common.domain.Status;
import com.school.api.common.exception.ConflictException;
import com.school.api.common.exception.ForbiddenException;
import com.school.api.common.exception.NotFoundException;
import com.school.api.common.util.TextNormalizer;
import com.school.api.common.web.PageResponse;
import com.school.api.school.dto.OwnershipHistoryResponse;
import com.school.api.school.dto.SchoolCreateRequest;
import com.school.api.school.dto.SchoolPatchRequest;
import com.school.api.school.dto.SchoolResponse;
import com.school.api.school.dto.TransferOwnerRequest;
import com.school.api.user.UserAccount;
import com.school.api.user.UserAccountRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchoolService {

    private final SchoolRepository schoolRepository;
    private final SchoolOwnershipHistoryRepository ownershipHistoryRepository;
    private final UserAccountRepository userRepository;

    public SchoolService(
            SchoolRepository schoolRepository,
            SchoolOwnershipHistoryRepository ownershipHistoryRepository,
            UserAccountRepository userRepository) {
        this.schoolRepository = schoolRepository;
        this.ownershipHistoryRepository = ownershipHistoryRepository;
        this.userRepository = userRepository;
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
    public void deactivate(UUID id, UUID deletedBy) {
        School school = find(id);
        school.setStatus(Status.INACTIVE);
        school.setDeletedAt(Instant.now());
        school.setDeletedBy(deletedBy);
    }

    /**
     * Hands the school to another user. Either the platform admin or the current owner
     * may execute it. The new owner joins the school (id + OWNER role), the previous
     * owner loses the OWNER role once they own no other school, and the transfer is
     * recorded in the ownership history.
     */
    @Transactional
    public SchoolResponse transferOwner(UUID id, TransferOwnerRequest request, UUID callerId, boolean callerIsPlatformAdmin) {
        School school = find(id);
        UUID newOwnerId = request.userId();
        if (newOwnerId.equals(school.getOwnerId())) {
            return SchoolResponse.from(school);
        }
        if (!callerIsPlatformAdmin && (callerId == null || !callerId.equals(school.getOwnerId()))) {
            throw new ForbiddenException("Only the platform admin or the current owner can transfer ownership");
        }
        UserAccount newOwner = userRepository.findById(newOwnerId)
                .orElseThrow(() -> new NotFoundException("User not found: " + newOwnerId));
        if (newOwner.getStatus() != Status.ACTIVE) {
            throw new ConflictException("User is not active: " + newOwnerId);
        }
        if (schoolRepository.existsByOwnerId(newOwnerId)) {
            throw new ConflictException("User already owns another school: " + newOwnerId);
        }
        if (newOwner.getSchoolId() != null && !newOwner.getSchoolId().equals(school.getId())) {
            throw new ConflictException("User already belongs to another school: " + newOwnerId);
        }

        UUID previousOwnerId = school.getOwnerId();
        school.setOwnerId(newOwnerId);
        newOwner.setSchoolId(school.getId());
        newOwner.getRoles().add(Role.OWNER);
        if (previousOwnerId != null) {
            revokeOwnerRoleIfUnset(previousOwnerId);
        }
        ownershipHistoryRepository.save(
                new SchoolOwnershipHistory(school.getId(), previousOwnerId, newOwnerId, callerId));
        return SchoolResponse.from(school);
    }

    @Transactional(readOnly = true)
    public List<OwnershipHistoryResponse> ownerHistory(UUID id) {
        find(id);
        List<SchoolOwnershipHistory> rows = ownershipHistoryRepository.findBySchoolIdOrderByTransferredAtDesc(id);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> usernames = new LinkedHashMap<>();
        userRepository.findAllById(rows.stream()
                        .flatMap(row -> Stream.of(row.getOwnerId(), row.getPreviousOwnerId(), row.getTransferredBy()))
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .forEach(user -> usernames.put(user.getId(), user.getUsername()));
        return rows.stream()
                .map(row -> new OwnershipHistoryResponse(
                        row.getSchoolId(),
                        row.getOwnerId(),
                        usernames.get(row.getOwnerId()),
                        row.getPreviousOwnerId(),
                        row.getPreviousOwnerId() == null ? null : usernames.get(row.getPreviousOwnerId()),
                        row.getTransferredBy(),
                        row.getTransferredBy() == null ? null : usernames.get(row.getTransferredBy()),
                        row.getTransferredAt()))
                .toList();
    }

    private void revokeOwnerRoleIfUnset(UUID userId) {
        if (schoolRepository.existsByOwnerId(userId)) {
            return;
        }
        userRepository.findById(userId).ifPresent(user -> user.getRoles().remove(Role.OWNER));
    }

    private School find(UUID id) {
        return schoolRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("School not found: " + id));
    }
}
