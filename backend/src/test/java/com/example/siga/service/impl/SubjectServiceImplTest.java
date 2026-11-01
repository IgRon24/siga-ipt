package com.example.siga.service.impl;

import com.example.siga.dto.SubjectRequestDTO;
import com.example.siga.dto.SubjectResponseDTO;
import com.example.siga.entity.Career;
import com.example.siga.entity.CareerStatus;
import com.example.siga.entity.Prerequisite;
import com.example.siga.entity.StudyPlan;
import com.example.siga.entity.StudyPlanStatus;
import com.example.siga.entity.Subject;
import com.example.siga.exception.BusinessException;
import com.example.siga.exception.ResourceNotFoundException;
import com.example.siga.mapper.SubjectMapper;
import com.example.siga.repository.AcademicHistoryRepository;
import com.example.siga.repository.EnrollmentRepository;
import com.example.siga.repository.PrerequisiteRepository;
import com.example.siga.repository.SectionRepository;
import com.example.siga.repository.StudyPlanRepository;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubjectServiceImplTest {

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private StudyPlanRepository studyPlanRepository;

    @Mock
    private PrerequisiteRepository prerequisiteRepository;

    @Mock
    private SectionRepository sectionRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private AcademicHistoryRepository academicHistoryRepository;

    private SubjectServiceImpl service;

    private StudyPlan plan;
    private StudyPlan otherPlan;
    private Subject existing;

    @BeforeEach
    void setUp() {
        service = new SubjectServiceImpl(
                subjectRepository, studyPlanRepository, prerequisiteRepository, sectionRepository,
                enrollmentRepository, academicHistoryRepository, new SubjectMapper());

        Career career = Career.builder()
                .id(10L)
                .code("10450")
                .name("Tecnico Analista Programador")
                .description("Carrera de dos anios")
                .status(CareerStatus.ACTIVA)
                .durationSemesters(4)
                .build();

        plan = StudyPlan.builder()
                .id(100L)
                .identifier("2021.3")
                .name("Plan 2021")
                .status(StudyPlanStatus.VIGENTE)
                .career(career)
                .build();

        otherPlan = StudyPlan.builder()
                .id(200L)
                .identifier("2022.3")
                .name("Plan 2022")
                .status(StudyPlanStatus.NO_VIGENTE)
                .career(career)
                .build();

        existing = subject(1L, "TAP201", "Estructuras de Datos", 2, plan);
    }

    private Subject subject(Long id, String code, String name, int semester, StudyPlan studyPlan) {
        return Subject.builder()
                .id(id)
                .code(code)
                .name(name)
                .semester(semester)
                .telTheory(3)
                .telExercises(2)
                .telLab(2)
                .sct(5)
                .studyPlan(studyPlan)
                .build();
    }

    private SubjectRequestDTO request() {
        return SubjectRequestDTO.builder()
                .code("TAP201")
                .name("Estructuras de Datos")
                .semester(2)
                .telTheory(3)
                .telExercises(2)
                .telLab(2)
                .sct(5)
                .studyPlanId(100L)
                .build();
    }

    // ---------- create ----------

    @Test
    @DisplayName("create: registra la asignatura en el plan indicado")
    void createShouldSaveSubject() {
        when(studyPlanRepository.findById(100L)).thenReturn(Optional.of(plan));
        when(subjectRepository.existsByStudyPlanIdAndCode(100L, "TAP201")).thenReturn(false);
        when(subjectRepository.save(any(Subject.class))).thenAnswer(inv -> {
            Subject saved = inv.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        SubjectResponseDTO result = service.create(request());

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getCode()).isEqualTo("TAP201");
        assertThat(result.getSemester()).isEqualTo(2);
        assertThat(result.getStudyPlanId()).isEqualTo(100L);

        ArgumentCaptor<Subject> captor = ArgumentCaptor.forClass(Subject.class);
        verify(subjectRepository).save(captor.capture());
        assertThat(captor.getValue().getStudyPlan()).isEqualTo(plan);
    }

    @Test
    @DisplayName("create: rechaza un codigo ya usado en el mismo plan")
    void createShouldRejectDuplicateCodeInSamePlan() {
        when(studyPlanRepository.findById(100L)).thenReturn(Optional.of(plan));
        when(subjectRepository.existsByStudyPlanIdAndCode(100L, "TAP201")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("code 'TAP201' already exists in study plan '2021.3'");

        verify(subjectRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: 404 si el plan de estudios no existe")
    void createShouldFailWhenStudyPlanNotFound() {
        when(studyPlanRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Study plan not found with id: 100");

        verify(subjectRepository, never()).save(any());
    }

    // ---------- lecturas ----------

    @Test
    @DisplayName("findById: devuelve la asignatura con su plan")
    void findByIdShouldReturnSubject() {
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(existing));

        SubjectResponseDTO result = service.findById(1L);

        assertThat(result.getName()).isEqualTo("Estructuras de Datos");
        assertThat(result.getStudyPlanId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("findById: 404 si la asignatura no existe")
    void findByIdShouldFailWhenNotFound() {
        when(subjectRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Subject not found with id: 999");
    }

    @Test
    @DisplayName("findAll: devuelve todas las asignaturas")
    void findAllShouldReturnSubjects() {
        when(subjectRepository.findAll()).thenReturn(List.of(existing));

        assertThat(service.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("findByStudyPlan: devuelve las asignaturas del plan")
    void findByStudyPlanShouldFilter() {
        when(studyPlanRepository.existsById(100L)).thenReturn(true);
        when(subjectRepository.findByStudyPlanId(100L)).thenReturn(List.of(existing));

        assertThat(service.findByStudyPlan(100L)).hasSize(1);
    }

    @Test
    @DisplayName("findByStudyPlan: 404 si el plan no existe")
    void findByStudyPlanShouldFailWhenPlanNotFound() {
        when(studyPlanRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> service.findByStudyPlan(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Study plan not found with id: 999");
    }

    // ---------- update ----------

    @Test
    @DisplayName("update: modifica los datos de la asignatura")
    void updateShouldModifySubject() {
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(studyPlanRepository.findById(100L)).thenReturn(Optional.of(plan));
        when(subjectRepository.save(any(Subject.class))).thenAnswer(inv -> inv.getArgument(0));

        SubjectRequestDTO dto = request();
        dto.setName("Estructuras de Datos Avanzadas");
        dto.setSct(6);

        SubjectResponseDTO result = service.update(1L, dto);

        assertThat(result.getName()).isEqualTo("Estructuras de Datos Avanzadas");
        assertThat(result.getSct()).isEqualTo(6);
    }

    @Test
    @DisplayName("update: permite guardar sin cambiar el codigo")
    void updateShouldAllowSameCode() {
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(studyPlanRepository.findById(100L)).thenReturn(Optional.of(plan));
        when(subjectRepository.save(any(Subject.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.update(1L, request())).isNotNull();

        verify(subjectRepository, never()).existsByStudyPlanIdAndCode(anyLong(), anyString());
    }

    @Test
    @DisplayName("update: rechaza un codigo ya usado en el mismo plan")
    void updateShouldRejectDuplicateCode() {
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(studyPlanRepository.findById(100L)).thenReturn(Optional.of(plan));
        when(subjectRepository.existsByStudyPlanIdAndCode(100L, "TAP202")).thenReturn(true);

        SubjectRequestDTO dto = request();
        dto.setCode("TAP202");

        assertThatThrownBy(() -> service.update(1L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("code 'TAP202' already exists in study plan '2021.3'");

        verify(subjectRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: 404 si la asignatura no existe")
    void updateShouldFailWhenNotFound() {
        when(subjectRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(999L, request()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Subject not found with id: 999");
    }

    @Test
    @DisplayName("update: 404 si el plan de estudios no existe")
    void updateShouldFailWhenStudyPlanNotFound() {
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(studyPlanRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(1L, request()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Study plan not found with id: 100");
    }

    @Test
    @DisplayName("update: mueve la asignatura de plan si no tiene prerrequisitos")
    void updateShouldAllowMovingPlanWithoutPrerequisites() {
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(studyPlanRepository.findById(200L)).thenReturn(Optional.of(otherPlan));
        when(subjectRepository.existsByStudyPlanIdAndCode(200L, "TAP201")).thenReturn(false);
        when(prerequisiteRepository.countBySubjectId(1L)).thenReturn(0L);
        when(prerequisiteRepository.existsByPrerequisiteSubjectId(1L)).thenReturn(false);
        when(subjectRepository.save(any(Subject.class))).thenAnswer(inv -> inv.getArgument(0));

        SubjectRequestDTO dto = request();
        dto.setStudyPlanId(200L);

        SubjectResponseDTO result = service.update(1L, dto);

        assertThat(result.getStudyPlanId()).isEqualTo(200L);
    }

    @Test
    @DisplayName("update: al mover de plan revisa que el codigo este libre en el plan destino")
    void updateShouldRejectMovingPlanWhenCodeIsTakenThere() {
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(studyPlanRepository.findById(200L)).thenReturn(Optional.of(otherPlan));
        when(subjectRepository.existsByStudyPlanIdAndCode(200L, "TAP201")).thenReturn(true);

        SubjectRequestDTO dto = request();
        dto.setStudyPlanId(200L);

        assertThatThrownBy(() -> service.update(1L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("code 'TAP201' already exists in study plan '2022.3'");

        verify(subjectRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: no mueve de plan una asignatura que tiene prerrequisitos")
    void updateShouldRejectMovingPlanWithOwnPrerequisites() {
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(studyPlanRepository.findById(200L)).thenReturn(Optional.of(otherPlan));
        when(subjectRepository.existsByStudyPlanIdAndCode(200L, "TAP201")).thenReturn(false);
        when(prerequisiteRepository.countBySubjectId(1L)).thenReturn(1L);

        SubjectRequestDTO dto = request();
        dto.setStudyPlanId(200L);

        assertThatThrownBy(() -> service.update(1L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot be moved to another study plan");

        verify(subjectRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: no mueve de plan una asignatura que es prerrequisito de otra")
    void updateShouldRejectMovingPlanWhenItIsAPrerequisite() {
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(studyPlanRepository.findById(200L)).thenReturn(Optional.of(otherPlan));
        when(subjectRepository.existsByStudyPlanIdAndCode(200L, "TAP201")).thenReturn(false);
        when(prerequisiteRepository.countBySubjectId(1L)).thenReturn(0L);
        when(prerequisiteRepository.existsByPrerequisiteSubjectId(1L)).thenReturn(true);

        SubjectRequestDTO dto = request();
        dto.setStudyPlanId(200L);

        assertThatThrownBy(() -> service.update(1L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot be moved to another study plan");

        verify(subjectRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: cambia de semestre si los prerrequisitos siguen siendo anteriores")
    void updateShouldAllowSemesterChangeWhenPrerequisitesStayValid() {
        Subject required = subject(2L, "TAP101", "Programacion", 1, plan);
        Subject dependent = subject(3L, "TAP301", "Bases de Datos", 4, plan);

        when(subjectRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(studyPlanRepository.findById(100L)).thenReturn(Optional.of(plan));
        when(prerequisiteRepository.findBySubjectId(1L))
                .thenReturn(List.of(Prerequisite.builder().id(1L).subject(existing).prerequisiteSubject(required).build()));
        when(prerequisiteRepository.findByPrerequisiteSubjectId(1L))
                .thenReturn(List.of(Prerequisite.builder().id(2L).subject(dependent).prerequisiteSubject(existing).build()));
        when(subjectRepository.save(any(Subject.class))).thenAnswer(inv -> inv.getArgument(0));

        SubjectRequestDTO dto = request();
        dto.setSemester(3);

        assertThat(service.update(1L, dto).getSemester()).isEqualTo(3);
    }

    @Test
    @DisplayName("update: rechaza bajar de semestre si un prerrequisito queda igual o posterior")
    void updateShouldRejectSemesterChangeBreakingOwnPrerequisite() {
        Subject required = subject(2L, "TAP101", "Programacion", 1, plan);

        when(subjectRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(studyPlanRepository.findById(100L)).thenReturn(Optional.of(plan));
        when(prerequisiteRepository.findBySubjectId(1L))
                .thenReturn(List.of(Prerequisite.builder().id(1L).subject(existing).prerequisiteSubject(required).build()));

        SubjectRequestDTO dto = request();
        dto.setSemester(1);

        assertThatThrownBy(() -> service.update(1L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("its prerequisite 'TAP101' belongs to semester 1");

        verify(subjectRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: rechaza subir de semestre si es prerrequisito de una asignatura anterior")
    void updateShouldRejectSemesterChangeBreakingDependentSubject() {
        Subject dependent = subject(3L, "TAP301", "Bases de Datos", 3, plan);

        when(subjectRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(studyPlanRepository.findById(100L)).thenReturn(Optional.of(plan));
        when(prerequisiteRepository.findBySubjectId(1L)).thenReturn(List.of());
        when(prerequisiteRepository.findByPrerequisiteSubjectId(1L))
                .thenReturn(List.of(Prerequisite.builder().id(2L).subject(dependent).prerequisiteSubject(existing).build()));

        SubjectRequestDTO dto = request();
        dto.setSemester(4);

        assertThatThrownBy(() -> service.update(1L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("it is a prerequisite of 'TAP301' (semester 3)");

        verify(subjectRepository, never()).save(any());
    }

    // ---------- delete ----------

    @Test
    @DisplayName("delete: elimina la asignatura y sus propios prerrequisitos")
    void deleteShouldRemoveSubjectAndItsPrerequisites() {
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(academicHistoryRepository.existsBySubjectCode("TAP201")).thenReturn(false);
        when(enrollmentRepository.existsBySubjectId(1L)).thenReturn(false);
        when(sectionRepository.existsBySubjectId(1L)).thenReturn(false);
        when(prerequisiteRepository.existsByPrerequisiteSubjectId(1L)).thenReturn(false);

        service.delete(1L);

        verify(prerequisiteRepository).deleteBySubjectId(1L);
        verify(subjectRepository).delete(existing);
    }

    @Test
    @DisplayName("delete: no elimina una asignatura que es prerrequisito de otra")
    void deleteShouldRejectWhenItIsAPrerequisite() {
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(academicHistoryRepository.existsBySubjectCode("TAP201")).thenReturn(false);
        when(enrollmentRepository.existsBySubjectId(1L)).thenReturn(false);
        when(sectionRepository.existsBySubjectId(1L)).thenReturn(false);
        when(prerequisiteRepository.existsByPrerequisiteSubjectId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("it is a prerequisite of another subject");

        verify(subjectRepository, never()).delete(any());
        verify(prerequisiteRepository, never()).deleteBySubjectId(anyLong());
    }

    @Test
    @DisplayName("delete: no elimina una asignatura con secciones en la oferta")
    void deleteShouldRejectWhenItHasSections() {
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(academicHistoryRepository.existsBySubjectCode("TAP201")).thenReturn(false);
        when(enrollmentRepository.existsBySubjectId(1L)).thenReturn(false);
        when(sectionRepository.existsBySubjectId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("has sections associated and cannot be deleted");

        verify(subjectRepository, never()).delete(any());
        verify(prerequisiteRepository, never()).deleteBySubjectId(anyLong());
    }

    @Test
    @DisplayName("delete: no elimina una asignatura que esta en el historial academico")
    void deleteShouldRejectWhenItIsInAcademicHistory() {
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(academicHistoryRepository.existsBySubjectCode("TAP201")).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("appears in the academic history and cannot be deleted");

        verify(subjectRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete: no elimina una asignatura con inscripciones")
    void deleteShouldRejectWhenItHasEnrollments() {
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(academicHistoryRepository.existsBySubjectCode("TAP201")).thenReturn(false);
        when(enrollmentRepository.existsBySubjectId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("has enrollments and cannot be deleted");

        verify(subjectRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete: 404 si la asignatura no existe")
    void deleteShouldFailWhenNotFound() {
        when(subjectRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Subject not found with id: 999");

        verify(subjectRepository, never()).delete(any());
    }
}
