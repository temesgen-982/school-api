package com.school.api.school;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.school.api.common.domain.Role;
import com.school.api.common.domain.Status;
import com.school.api.common.exception.ConflictException;
import com.school.api.common.exception.ForbiddenException;
import com.school.api.common.exception.NotFoundException;
import com.school.api.school.dto.OwnershipHistoryResponse;
import com.school.api.school.dto.SchoolCreateRequest;
import com.school.api.school.dto.SchoolPatchRequest;
import com.school.api.school.dto.SchoolResponse;
import com.school.api.school.dto.TransferOwnerRequest;
import com.school.api.user.UserAccount;
import com.school.api.user.UserAccountRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SchoolServiceTest {

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private SchoolOwnershipHistoryRepository ownershipHistoryRepository;

    @Mock
    private UserAccountRepository userRepository;

    private SchoolService schoolService;

    @BeforeEach
    void setUp() {
        schoolService = new SchoolService(schoolRepository, ownershipHistoryRepository, userRepository);
    }

    @Test
    void createNormalizesCodeAndDefaultsToActive() {
        when(schoolRepository.existsByCode("bright-future")).thenReturn(false);

        schoolService.create(new SchoolCreateRequest("Bright Future", "Bright-Future", null, null));

        ArgumentCaptor<School> captor = ArgumentCaptor.forClass(School.class);
        verify(schoolRepository).save(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("bright-future");
        assertThat(captor.getValue().getStatus()).isEqualTo(Status.ACTIVE);
    }

    @Test
    void createRejectsDuplicateCode() {
        when(schoolRepository.existsByCode("bright-future")).thenReturn(true);

        assertThatThrownBy(() -> schoolService.create(
                new SchoolCreateRequest("Other School", "bright-future", null, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("bright-future");
    }

    @Test
    void getByIdThrowsNotFoundWhenMissing() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> schoolService.getById(id))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void patchChangesNameAndKeepsUnchangedCode() {
        School school = new School();
        school.setName("Old Name");
        school.setCode("old-code");
        UUID id = UUID.randomUUID();
        when(schoolRepository.findById(id)).thenReturn(Optional.of(school));

        schoolService.patch(id, new SchoolPatchRequest("New Name", "old-code", null, null));

        assertThat(school.getName()).isEqualTo("New Name");
        assertThat(school.getCode()).isEqualTo("old-code");
    }

    @Test
    void patchRejectsCodeTakenByAnotherSchool() {
        School school = new School();
        school.setName("Mine");
        school.setCode("mine");
        UUID id = UUID.randomUUID();
        when(schoolRepository.findById(id)).thenReturn(Optional.of(school));
        when(schoolRepository.existsByCode("taken")).thenReturn(true);

        assertThatThrownBy(() -> schoolService.patch(id, new SchoolPatchRequest(null, "Taken", null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void deactivateSetsInactiveAndRecordsWhoDeleted() {
        School school = new School();
        school.setStatus(Status.ACTIVE);
        UUID id = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        when(schoolRepository.findById(id)).thenReturn(Optional.of(school));

        schoolService.deactivate(id, operatorId);

        assertThat(school.getStatus()).isEqualTo(Status.INACTIVE);
        assertThat(school.getDeletedAt()).isNotNull();
        assertThat(school.getDeletedBy()).isEqualTo(operatorId);
    }

    @Test
    void transferOwnerAssignsSchoolGrantsRoleAndRecordsHistory() {
        School school = new School();
        school.setName("Bright Future");
        UUID schoolId = UUID.randomUUID();
        UUID newOwnerId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        ReflectionTestUtils.setField(school, "id", schoolId);
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        UserAccount newOwner = user(newOwnerId, Status.ACTIVE);
        when(userRepository.findById(newOwnerId)).thenReturn(Optional.of(newOwner));
        when(schoolRepository.existsByOwnerId(newOwnerId)).thenReturn(false);

        SchoolResponse response = schoolService.transferOwner(
                schoolId, new TransferOwnerRequest(newOwnerId), callerId, true);

        assertThat(response.ownerId()).isEqualTo(newOwnerId);
        assertThat(school.getOwnerId()).isEqualTo(newOwnerId);
        assertThat(newOwner.getSchoolId()).isEqualTo(schoolId);
        assertThat(newOwner.getRoles()).contains(Role.OWNER);
        ArgumentCaptor<SchoolOwnershipHistory> captor = ArgumentCaptor.forClass(SchoolOwnershipHistory.class);
        verify(ownershipHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getSchoolId()).isEqualTo(schoolId);
        assertThat(captor.getValue().getPreviousOwnerId()).isNull();
        assertThat(captor.getValue().getOwnerId()).isEqualTo(newOwnerId);
        assertThat(captor.getValue().getTransferredBy()).isEqualTo(callerId);
    }

    @Test
    void transferOwnerRevokesRoleFromPreviousOwner() {
        School school = new School();
        UUID schoolId = UUID.randomUUID();
        UUID previousOwnerId = UUID.randomUUID();
        UUID newOwnerId = UUID.randomUUID();
        school.setOwnerId(previousOwnerId);
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        UserAccount previousOwner = user(previousOwnerId, Status.ACTIVE);
        previousOwner.setRoles(new HashSet<>(Set.of(Role.OWNER, Role.SCHOOL_ADMIN)));
        UserAccount newOwner = user(newOwnerId, Status.ACTIVE);
        when(userRepository.findById(previousOwnerId)).thenReturn(Optional.of(previousOwner));
        when(userRepository.findById(newOwnerId)).thenReturn(Optional.of(newOwner));
        when(schoolRepository.existsByOwnerId(newOwnerId)).thenReturn(false);
        when(schoolRepository.existsByOwnerId(previousOwnerId)).thenReturn(false);

        schoolService.transferOwner(schoolId, new TransferOwnerRequest(newOwnerId), UUID.randomUUID(), true);

        assertThat(previousOwner.getRoles()).doesNotContain(Role.OWNER).contains(Role.SCHOOL_ADMIN);
        assertThat(newOwner.getRoles()).contains(Role.OWNER);
    }

    @Test
    void transferOwnerRejectsCallerWhoIsNeitherPlatformAdminNorOwner() {
        School school = new School();
        UUID schoolId = UUID.randomUUID();
        school.setOwnerId(UUID.randomUUID());
        UUID callerId = UUID.randomUUID();
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));

        assertThatThrownBy(() -> schoolService.transferOwner(
                schoolId, new TransferOwnerRequest(UUID.randomUUID()), callerId, false))
                .isInstanceOf(ForbiddenException.class);

        verify(ownershipHistoryRepository, never()).save(any());
    }

    @Test
    void transferOwnerRejectsUnknownUser() {
        School school = new School();
        UUID schoolId = UUID.randomUUID();
        UUID missingUserId = UUID.randomUUID();
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        when(userRepository.findById(missingUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> schoolService.transferOwner(
                schoolId, new TransferOwnerRequest(missingUserId), null, true))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(missingUserId.toString());
    }

    @Test
    void transferOwnerRejectsInactiveUser() {
        School school = new School();
        UUID schoolId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, Status.INACTIVE)));

        assertThatThrownBy(() -> schoolService.transferOwner(
                schoolId, new TransferOwnerRequest(userId), null, true))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void transferOwnerRejectsUserWhoOwnsAnotherSchool() {
        School school = new School();
        UUID schoolId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, Status.ACTIVE)));
        when(schoolRepository.existsByOwnerId(userId)).thenReturn(true);

        assertThatThrownBy(() -> schoolService.transferOwner(
                schoolId, new TransferOwnerRequest(userId), null, true))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("another school");
    }

    @Test
    void transferOwnerRejectsUserAlreadyBelongingToAnotherSchool() {
        School school = new School();
        UUID schoolId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        UserAccount user = user(userId, Status.ACTIVE);
        user.setSchoolId(UUID.randomUUID());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(schoolRepository.existsByOwnerId(userId)).thenReturn(false);

        assertThatThrownBy(() -> schoolService.transferOwner(
                schoolId, new TransferOwnerRequest(userId), null, true))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("belongs to another school");
    }

    @Test
    void transferOwnerToCurrentOwnerIsANoOp() {
        School school = new School();
        UUID schoolId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        school.setOwnerId(ownerId);
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));

        SchoolResponse response = schoolService.transferOwner(
                schoolId, new TransferOwnerRequest(ownerId), null, false);

        assertThat(response.ownerId()).isEqualTo(ownerId);
        verify(ownershipHistoryRepository, never()).save(any());
    }

    @Test
    void ownerHistoryResolvesUsernames() {
        School school = new School();
        UUID schoolId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID transferredBy = UUID.randomUUID();
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        when(ownershipHistoryRepository.findBySchoolIdOrderByTransferredAtDesc(schoolId)).thenReturn(List.of(
                new SchoolOwnershipHistory(schoolId, null, ownerId, transferredBy)));
        UserAccount owner = user(ownerId, Status.ACTIVE);
        owner.setUsername("kinder");
        UserAccount executor = user(transferredBy, Status.ACTIVE);
        executor.setUsername("admin");
        when(userRepository.findAllById(any())).thenReturn(List.of(owner, executor));

        List<OwnershipHistoryResponse> history = schoolService.ownerHistory(schoolId);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).ownerUsername()).isEqualTo("kinder");
        assertThat(history.get(0).previousOwnerUsername()).isNull();
        assertThat(history.get(0).transferredByUsername()).isEqualTo("admin");
        assertThat(history.get(0).transferredAt()).isNotNull();
    }

    @Test
    void ownerHistoryThrowsNotFoundWhenSchoolMissing() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> schoolService.ownerHistory(id))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    private static UserAccount user(UUID id, Status status) {
        UserAccount user = new UserAccount();
        user.setUsername("user-" + id);
        user.setPassword("secret");
        user.setStatus(status);
        user.setRoles(new HashSet<>(Set.of(Role.SCHOOL_ADMIN)));
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
