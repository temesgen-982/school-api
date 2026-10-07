package com.school.api.school;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.school.api.common.domain.Status;
import com.school.api.common.exception.ConflictException;
import com.school.api.common.exception.NotFoundException;
import com.school.api.school.dto.SchoolCreateRequest;
import com.school.api.school.dto.SchoolPatchRequest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SchoolServiceTest {

    @Mock
    private SchoolRepository schoolRepository;

    private SchoolService schoolService;

    @BeforeEach
    void setUp() {
        schoolService = new SchoolService(schoolRepository);
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
}
