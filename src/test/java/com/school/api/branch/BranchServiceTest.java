package com.school.api.branch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.school.api.branch.dto.BranchCreateRequest;
import com.school.api.branch.dto.BranchPatchRequest;
import com.school.api.branch.dto.BranchResponse;
import com.school.api.common.exception.ConflictException;
import com.school.api.common.exception.NotFoundException;
import com.school.api.school.School;
import com.school.api.school.SchoolRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class BranchServiceTest {

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private SchoolRepository schoolRepository;

    private BranchService branchService;

    @BeforeEach
    void setUp() {
        branchService = new BranchService(branchRepository, schoolRepository);
    }

    @Test
    void createFailsWhenSchoolMissing() {
        UUID schoolId = UUID.randomUUID();
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> branchService.create(
                schoolId, new BranchCreateRequest("Main Campus", "main", null, null, null, null, null)))
                .isInstanceOf(NotFoundException.class);

        verify(branchRepository, never()).save(any(Branch.class));
    }

    @Test
    void createRejectsDuplicateCodeWithinSchool() {
        UUID schoolId = UUID.randomUUID();
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(new School()));
        when(branchRepository.existsBySchoolIdAndCode(schoolId, "main")).thenReturn(true);

        assertThatThrownBy(() -> branchService.create(
                schoolId, new BranchCreateRequest("Main Campus", "Main", null, null, null, null, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("main");
    }

    @Test
    void createDemotesExistingPrimaryBranch() {
        UUID schoolId = UUID.randomUUID();
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(new School()));
        when(branchRepository.existsBySchoolIdAndCode(schoolId, "main")).thenReturn(false);
        Branch existingPrimary = new Branch();
        existingPrimary.setPrimary(true);
        when(branchRepository.findBySchoolIdAndPrimaryTrue(schoolId)).thenReturn(List.of(existingPrimary));

        branchService.create(schoolId,
                new BranchCreateRequest("Main Campus", "main", true, null, null, null, null));

        assertThat(existingPrimary.isPrimary()).isFalse();
        ArgumentCaptor<Branch> captor = ArgumentCaptor.forClass(Branch.class);
        verify(branchRepository).save(captor.capture());
        assertThat(captor.getValue().isPrimary()).isTrue();
    }

    @Test
    void listThrowsNotFoundWhenSchoolMissing() {
        UUID schoolId = UUID.randomUUID();
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> branchService.list(schoolId, PageRequest.of(0, 20)))
                .isInstanceOf(NotFoundException.class);

        verify(branchRepository, never()).findBySchoolId(any(), any());
    }

    @Test
    void getByIdIsScopedToSchool() {
        UUID schoolId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(branchRepository.findByIdAndSchoolId(id, schoolId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> branchService.getById(schoolId, id))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void patchPromotingToPrimaryDemotesOthers() {
        UUID schoolId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        Branch target = new Branch();
        target.setSchoolId(schoolId);
        target.setPrimary(false);
        when(branchRepository.findByIdAndSchoolId(id, schoolId)).thenReturn(Optional.of(target));
        Branch other = new Branch();
        other.setPrimary(true);
        when(branchRepository.findBySchoolIdAndPrimaryTrue(schoolId)).thenReturn(List.of(other));

        BranchResponse response = branchService.patch(schoolId, id,
                new BranchPatchRequest(null, null, true, null, null, null, null));

        assertThat(response.primary()).isTrue();
        assertThat(other.isPrimary()).isFalse();
    }

    @Test
    void deactivateIsScopedToSchoolAndRecordsWhoDeleted() {
        UUID schoolId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        Branch branch = new Branch();
        branch.setSchoolId(schoolId);
        when(branchRepository.findByIdAndSchoolId(id, schoolId)).thenReturn(Optional.of(branch));

        branchService.deactivate(schoolId, id, operatorId);

        assertThat(branch.getStatus()).isEqualTo(com.school.api.common.domain.Status.INACTIVE);
        assertThat(branch.getDeletedAt()).isNotNull();
        assertThat(branch.getDeletedBy()).isEqualTo(operatorId);
    }
}
