package com.school.api.auth;

import com.school.api.auth.dto.SignupRequest;
import com.school.api.auth.dto.SignupResponse;
import com.school.api.branch.Branch;
import com.school.api.branch.BranchRepository;
import com.school.api.branch.dto.BranchResponse;
import com.school.api.common.domain.Role;
import com.school.api.common.domain.Status;
import com.school.api.common.exception.ConflictException;
import com.school.api.common.util.TextNormalizer;
import com.school.api.school.School;
import com.school.api.school.SchoolOwnershipHistory;
import com.school.api.school.SchoolOwnershipHistoryRepository;
import com.school.api.school.SchoolRepository;
import com.school.api.school.dto.SchoolResponse;
import com.school.api.user.UserAccount;
import com.school.api.user.UserAccountRepository;
import com.school.api.user.dto.UserResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates an owner account, their school, and the school's initial branches in one
 * atomic step. Roles are assigned by the server ({@code OWNER} + {@code SCHOOL_ADMIN});
 * the payload cannot influence them.
 */
@Service
public class SignupService {

    private final UserAccountRepository userRepository;
    private final SchoolRepository schoolRepository;
    private final BranchRepository branchRepository;
    private final SchoolOwnershipHistoryRepository ownershipHistoryRepository;
    private final PasswordEncoder passwordEncoder;

    public SignupService(
            UserAccountRepository userRepository,
            SchoolRepository schoolRepository,
            BranchRepository branchRepository,
            SchoolOwnershipHistoryRepository ownershipHistoryRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.schoolRepository = schoolRepository;
        this.branchRepository = branchRepository;
        this.ownershipHistoryRepository = ownershipHistoryRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        String username = request.owner().username().trim();
        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("Username already exists: " + username);
        }
        String schoolCode = TextNormalizer.normalizeCode(request.school().code());
        if (schoolRepository.existsByCode(schoolCode)) {
            throw new ConflictException("School code already exists: " + schoolCode);
        }
        List<String> branchCodes = normalizeBranchCodes(request.branches());
        long primaryCount = request.branches().stream()
                .filter(branch -> Boolean.TRUE.equals(branch.primary()))
                .count();
        if (primaryCount > 1) {
            throw new ConflictException("Only one branch can be marked as primary");
        }

        UserAccount owner = new UserAccount();
        owner.setUsername(username);
        owner.setPassword(passwordEncoder.encode(request.owner().password()));
        owner.setFirstName(TextNormalizer.trimToNull(request.owner().firstName()));
        owner.setMiddleName(TextNormalizer.trimToNull(request.owner().middleName()));
        owner.setLastName(TextNormalizer.trimToNull(request.owner().lastName()));
        owner.setStatus(Status.ACTIVE);
        owner.setRoles(new LinkedHashSet<>(Set.of(Role.OWNER, Role.SCHOOL_ADMIN)));
        userRepository.save(owner);

        School school = new School();
        school.setName(request.school().name().trim());
        school.setCode(schoolCode);
        school.setPhone(TextNormalizer.trimToNull(request.school().phone()));
        school.setEmail(TextNormalizer.trimToNull(request.school().email()));
        school.setStatus(Status.ACTIVE);
        school.setOwnerId(owner.getId());
        schoolRepository.save(school);

        owner.setSchoolId(school.getId());
        ownershipHistoryRepository.save(
                new SchoolOwnershipHistory(school.getId(), null, owner.getId(), null));

        boolean anyPrimary = primaryCount == 1;
        List<BranchResponse> branches = new ArrayList<>();
        for (int i = 0; i < request.branches().size(); i++) {
            SignupRequest.BranchInput input = request.branches().get(i);
            Branch branch = new Branch();
            branch.setSchoolId(school.getId());
            branch.setName(input.name().trim());
            branch.setCode(branchCodes.get(i));
            branch.setAddressLine(TextNormalizer.trimToNull(input.addressLine()));
            branch.setCity(TextNormalizer.trimToNull(input.city()));
            branch.setRegion(TextNormalizer.trimToNull(input.region()));
            branch.setPhone(TextNormalizer.trimToNull(input.phone()));
            branch.setStatus(Status.ACTIVE);
            branch.setPrimary(Boolean.TRUE.equals(input.primary()) || (!anyPrimary && i == 0));
            branches.add(BranchResponse.from(branchRepository.save(branch)));
        }

        return new SignupResponse(UserResponse.from(owner), SchoolResponse.from(school), branches);
    }

    private List<String> normalizeBranchCodes(List<SignupRequest.BranchInput> branches) {
        List<String> codes = new ArrayList<>(branches.size());
        Set<String> seen = new HashSet<>();
        for (SignupRequest.BranchInput branch : branches) {
            String code = TextNormalizer.normalizeCode(branch.code());
            if (!seen.add(code)) {
                throw new ConflictException("Branch code already exists in this request: " + code);
            }
            codes.add(code);
        }
        return codes;
    }
}
