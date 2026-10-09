package com.school.api.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.school.api.common.domain.Role;
import com.school.api.common.domain.Status;
import com.school.api.common.exception.ConflictException;
import com.school.api.common.exception.NotFoundException;
import com.school.api.school.SchoolRepository;
import com.school.api.user.dto.UserCreateRequest;
import com.school.api.user.dto.UserResponse;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserAccountRepository userRepository;

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, schoolRepository, passwordEncoder);
    }

    @Test
    void createEncodesPasswordAndDefaultsToActive() {
        when(userRepository.existsByUsername("kinder")).thenReturn(false);
        when(passwordEncoder.encode("s3cret-pass")).thenReturn("encoded");
        when(userRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.create(new UserCreateRequest(
                "kinder", "s3cret-pass", "Kin", null, "Derd", Set.of(Role.SCHOOL_ADMIN), null));

        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userRepository).save(captor.capture());
        UserAccount saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("kinder");
        assertThat(saved.getPassword()).isEqualTo("encoded");
        assertThat(saved.getStatus()).isEqualTo(Status.ACTIVE);
        assertThat(saved.getRoles()).containsExactly(Role.SCHOOL_ADMIN);
        assertThat(response.username()).isEqualTo("kinder");
        assertThat(response.roles()).containsExactly(Role.SCHOOL_ADMIN);
    }

    @Test
    void createRejectsDuplicateUsername() {
        when(userRepository.existsByUsername("kinder")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(new UserCreateRequest(
                "kinder", "s3cret-pass", null, null, null, Set.of(Role.SCHOOL_ADMIN), null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("kinder");
    }

    @Test
    void createRejectsUnknownSchool() {
        UUID schoolId = UUID.randomUUID();
        when(userRepository.existsByUsername("kinder")).thenReturn(false);
        when(schoolRepository.existsById(schoolId)).thenReturn(false);

        assertThatThrownBy(() -> userService.create(new UserCreateRequest(
                "kinder", "s3cret-pass", null, null, null, Set.of(Role.SCHOOL_ADMIN), schoolId)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(schoolId.toString());
    }

    @Test
    void createAssignsSchoolWhenItExists() {
        UUID schoolId = UUID.randomUUID();
        when(userRepository.existsByUsername("kinder")).thenReturn(false);
        when(schoolRepository.existsById(schoolId)).thenReturn(true);
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(userRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.create(new UserCreateRequest(
                "kinder", "s3cret-pass", null, null, null, Set.of(Role.OWNER), schoolId));

        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getSchoolId()).isEqualTo(schoolId);
    }

    @Test
    void getByIdThrowsNotFoundWhenMissing() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(id))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void getByIdReturnsUser() {
        UserAccount user = new UserAccount();
        user.setUsername("kinder");
        user.setPassword("encoded");
        user.setStatus(Status.ACTIVE);
        user.setRoles(new HashSet<>(Set.of(Role.OWNER)));
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        UserResponse response = userService.getById(id);

        assertThat(response.username()).isEqualTo("kinder");
        assertThat(response.roles()).containsExactly(Role.OWNER);
    }
}
