package com.school.api.user;

import com.school.api.common.domain.Status;
import com.school.api.common.exception.ConflictException;
import com.school.api.common.exception.NotFoundException;
import com.school.api.common.util.TextNormalizer;
import com.school.api.school.SchoolRepository;
import com.school.api.user.dto.UserCreateRequest;
import com.school.api.user.dto.UserResponse;
import java.util.LinkedHashSet;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserAccountRepository userRepository;
    private final SchoolRepository schoolRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserAccountRepository userRepository,
            SchoolRepository schoolRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.schoolRepository = schoolRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        String username = request.username().trim();
        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("Username already exists: " + username);
        }
        if (request.schoolId() != null && !schoolRepository.existsById(request.schoolId())) {
            throw new NotFoundException("School not found: " + request.schoolId());
        }
        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setFirstName(TextNormalizer.trimToNull(request.firstName()));
        user.setMiddleName(TextNormalizer.trimToNull(request.middleName()));
        user.setLastName(TextNormalizer.trimToNull(request.lastName()));
        user.setSchoolId(request.schoolId());
        user.setStatus(Status.ACTIVE);
        user.setRoles(new LinkedHashSet<>(request.roles()));
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public UserResponse getById(UUID id) {
        return UserResponse.from(userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id)));
    }
}
