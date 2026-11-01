package com.example.siga.service.impl;

import com.example.siga.dto.PrerequisiteRequestDTO;
import com.example.siga.dto.PrerequisiteResponseDTO;
import com.example.siga.entity.Career;
import com.example.siga.entity.Prerequisite;
import com.example.siga.entity.StudyPlan;
import com.example.siga.entity.Subject;
import com.example.siga.exception.BusinessException;
import com.example.siga.exception.ResourceNotFoundException;
import com.example.siga.mapper.PrerequisiteMapper;
import com.example.siga.repository.PrerequisiteRepository;
import com.example.siga.repository.SubjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrerequisiteServiceImplTest {

    @Mock
    private PrerequisiteRepository prerequisiteRepository;

    @Mock
    private SubjectRepository subjectRepository;

    private PrerequisiteServiceImpl service;

    private Subject programming1;     // semestre 1, plan A
    private Subject programming2;     // semestre 2, plan A
    private Subject databases;        // semestre 2, plan A
    private Subject otherPlanSubject; // semestre 1, plan B

    @BeforeEach
    void setUp() {
        service = new PrerequisiteServiceImpl(prerequisiteRepository, subjectRepository, new PrerequisiteMapper());

        Career career = Career.builder().id(1L).code("10450").build();
        StudyPlan planA = StudyPlan.builder()
                .id(10L).identifier("2021.3").name("Plan 2021.3").career(career).build();
        StudyPlan planB = StudyPlan.builder()
                .id(20L).identifier("2022.3").name("Plan 2022.3").career(career).build();

        programming1 = subject(100L, "TAP101", "Programacion I", 1, planA);
        programming2 = subject(200L, "TAP201", "Programacion II", 2, planA);
        databases = subject(300L, "TAP202", "Bases de Datos", 2, planA);
        otherPlanSubject = subject(400L, "TAD101", "Estadistica", 1, planB);
    }

    private Subject subject(Long id, String code, String name, int semester, StudyPlan plan) {
        return Subject.builder()
                .id(id).code(code).name(name).semester(semester)
                .telTheory(2).telExercises(2).telLab(2).sct(5)
                .studyPlan(plan)
                .build();
    }

    // ---------- add: casos felices ----------

    @Test
    @DisplayName("add: registra un prerrequisito del mismo plan y de un semestre anterior")
    void addShouldCreatePrerequisite() {
        when(subjectRepository.findById(200L)).thenReturn(Optional.of(programming2));
        when(subjectRepository.findById(100L)).thenReturn(Optional.of(programming1));
        when(prerequisiteRepository.existsBySubjectIdAndPrerequisiteSubjectId(200L, 100L)).thenReturn(false);
        when(prerequisiteRepository.countBySubjectId(200L)).thenReturn(0L);
        when(prerequisiteRepository.save(any(Prerequisite.class))).thenAnswer(inv -> {
            Prerequisite saved = inv.getArgument(0);
            saved.setId(999L);
            return saved;
        });

        PrerequisiteResponseDTO result = service.add(200L, new PrerequisiteRequestDTO(100L));

        assertThat(result.getId()).isEqualTo(999L);
        assertThat(result.getSubjectId()).isEqualTo(200L);
        assertThat(result.getSubjectCode()).isEqualTo("TAP201");
        assertThat(result.getPrerequisiteSubjectId()).isEqualTo(100L);
        assertThat(result.getPrerequisiteSubjectCode()).isEqualTo("TAP101");
        assertThat(result.getPrerequisiteSubjectSemester()).isEqualTo(1);

        ArgumentCaptor<Prerequisite> captor = ArgumentCaptor.forClass(Prerequisite.class);
        verify(prerequisiteRepository).save(captor.capture());
        assertThat(captor.getValue().getSubject()).isEqualTo(programming2);
        assertThat(captor.getValue().getPrerequisiteSubject()).isEqualTo(programming1);
    }

    @Test
    @DisplayName("add: acepta el tercer prerrequisito (limite permitido)")
    void addShouldAcceptThirdPrerequisite() {
        when(subjectRepository.findById(200L)).thenReturn(Optional.of(programming2));
        when(subjectRepository.findById(100L)).thenReturn(Optional.of(programming1));
        when(prerequisiteRepository.existsBySubjectIdAndPrerequisiteSubjectId(200L, 100L)).thenReturn(false);
        when(prerequisiteRepository.countBySubjectId(200L)).thenReturn(2L);
        when(prerequisiteRepository.save(any(Prerequisite.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.add(200L, new PrerequisiteRequestDTO(100L))).isNotNull();
        verify(prerequisiteRepository).save(any(Prerequisite.class));
    }

    // ---------- add: casos de rechazo ----------

    @Test
    @DisplayName("add: 404 si la asignatura no existe")
    void addShouldFailWhenSubjectNotFound() {
        when(subjectRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.add(999L, new PrerequisiteRequestDTO(100L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Subject not found with id: 999");

        verify(prerequisiteRepository, never()).save(any());
    }

    @Test
    @DisplayName("add: 404 si la asignatura prerrequisito no existe")
    void addShouldFailWhenPrerequisiteSubjectNotFound() {
        when(subjectRepository.findById(200L)).thenReturn(Optional.of(programming2));
        when(subjectRepository.findById(777L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.add(200L, new PrerequisiteRequestDTO(777L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Subject not found with id: 777");

        verify(prerequisiteRepository, never()).save(any());
    }

    @Test
    @DisplayName("add: rechaza que una asignatura sea prerrequisito de si misma")
    void addShouldRejectSelfReference() {
        when(subjectRepository.findById(200L)).thenReturn(Optional.of(programming2));

        assertThatThrownBy(() -> service.add(200L, new PrerequisiteRequestDTO(200L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot be a prerequisite of itself");

        verify(prerequisiteRepository, never()).save(any());
    }

    @Test
    @DisplayName("add: rechaza un prerrequisito de otro plan de estudios")
    void addShouldRejectPrerequisiteFromAnotherStudyPlan() {
        when(subjectRepository.findById(200L)).thenReturn(Optional.of(programming2));
        when(subjectRepository.findById(400L)).thenReturn(Optional.of(otherPlanSubject));

        assertThatThrownBy(() -> service.add(200L, new PrerequisiteRequestDTO(400L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("must belong to the same study plan");

        verify(prerequisiteRepository, never()).save(any());
    }

    @Test
    @DisplayName("add: rechaza un prerrequisito del mismo semestre")
    void addShouldRejectPrerequisiteFromSameSemester() {
        when(subjectRepository.findById(200L)).thenReturn(Optional.of(programming2));
        when(subjectRepository.findById(300L)).thenReturn(Optional.of(databases));

        assertThatThrownBy(() -> service.add(200L, new PrerequisiteRequestDTO(300L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("must belong to a semester before");

        verify(prerequisiteRepository, never()).save(any());
    }

    @Test
    @DisplayName("add: rechaza un prerrequisito de un semestre posterior")
    void addShouldRejectPrerequisiteFromLaterSemester() {
        when(subjectRepository.findById(100L)).thenReturn(Optional.of(programming1));
        when(subjectRepository.findById(200L)).thenReturn(Optional.of(programming2));

        assertThatThrownBy(() -> service.add(100L, new PrerequisiteRequestDTO(200L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("must belong to a semester before");

        verify(prerequisiteRepository, never()).save(any());
    }

    @Test
    @DisplayName("add: rechaza un prerrequisito duplicado")
    void addShouldRejectDuplicatePrerequisite() {
        when(subjectRepository.findById(200L)).thenReturn(Optional.of(programming2));
        when(subjectRepository.findById(100L)).thenReturn(Optional.of(programming1));
        when(prerequisiteRepository.existsBySubjectIdAndPrerequisiteSubjectId(200L, 100L)).thenReturn(true);

        assertThatThrownBy(() -> service.add(200L, new PrerequisiteRequestDTO(100L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already has");

        verify(prerequisiteRepository, never()).save(any());
    }

    @Test
    @DisplayName("add: rechaza un cuarto prerrequisito")
    void addShouldRejectFourthPrerequisite() {
        when(subjectRepository.findById(200L)).thenReturn(Optional.of(programming2));
        when(subjectRepository.findById(100L)).thenReturn(Optional.of(programming1));
        when(prerequisiteRepository.existsBySubjectIdAndPrerequisiteSubjectId(200L, 100L)).thenReturn(false);
        when(prerequisiteRepository.countBySubjectId(200L)).thenReturn(3L);

        assertThatThrownBy(() -> service.add(200L, new PrerequisiteRequestDTO(100L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot have more than 3 prerequisites");

        verify(prerequisiteRepository, never()).save(any());
    }

    // ---------- findBySubject ----------

    @Test
    @DisplayName("findBySubject: devuelve los prerrequisitos de la asignatura")
    void findBySubjectShouldReturnPrerequisites() {
        Prerequisite prerequisite = Prerequisite.builder()
                .id(1L).subject(programming2).prerequisiteSubject(programming1).build();

        when(subjectRepository.existsById(200L)).thenReturn(true);
        when(prerequisiteRepository.findBySubjectId(200L)).thenReturn(List.of(prerequisite));

        List<PrerequisiteResponseDTO> result = service.findBySubject(200L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPrerequisiteSubjectCode()).isEqualTo("TAP101");
        assertThat(result.get(0).getPrerequisiteSubjectName()).isEqualTo("Programacion I");
    }

    @Test
    @DisplayName("findBySubject: devuelve lista vacia si la asignatura no tiene prerrequisitos")
    void findBySubjectShouldReturnEmptyList() {
        when(subjectRepository.existsById(100L)).thenReturn(true);
        when(prerequisiteRepository.findBySubjectId(100L)).thenReturn(List.of());

        assertThat(service.findBySubject(100L)).isEmpty();
    }

    @Test
    @DisplayName("findBySubject: 404 si la asignatura no existe")
    void findBySubjectShouldFailWhenSubjectNotFound() {
        when(subjectRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> service.findBySubject(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Subject not found with id: 999");
    }

    // ---------- remove ----------

    @Test
    @DisplayName("remove: elimina el prerrequisito existente")
    void removeShouldDeletePrerequisite() {
        Prerequisite prerequisite = Prerequisite.builder()
                .id(1L).subject(programming2).prerequisiteSubject(programming1).build();

        when(subjectRepository.existsById(200L)).thenReturn(true);
        when(prerequisiteRepository.findBySubjectIdAndPrerequisiteSubjectId(200L, 100L))
                .thenReturn(Optional.of(prerequisite));

        service.remove(200L, 100L);

        verify(prerequisiteRepository).delete(prerequisite);
    }

    @Test
    @DisplayName("remove: 404 si la asignatura no existe")
    void removeShouldFailWhenSubjectNotFound() {
        when(subjectRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> service.remove(999L, 100L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Subject not found with id: 999");

        verify(prerequisiteRepository, never()).delete(any());
    }

    @Test
    @DisplayName("remove: 404 si la relacion de prerrequisito no existe")
    void removeShouldFailWhenPrerequisiteNotFound() {
        when(subjectRepository.existsById(200L)).thenReturn(true);
        when(prerequisiteRepository.findBySubjectIdAndPrerequisiteSubjectId(200L, 100L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remove(200L, 100L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("has no prerequisite with subject id 100");

        verify(prerequisiteRepository, never()).delete(any());
    }
}
