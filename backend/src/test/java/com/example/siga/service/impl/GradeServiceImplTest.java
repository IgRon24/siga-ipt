package com.example.siga.service.impl;

import com.example.siga.dto.GradeRequestDTO;
import com.example.siga.dto.GradeResponseDTO;
import com.example.siga.entity.AcademicHistoryResult;
import com.example.siga.entity.AcademicPeriod;
import com.example.siga.entity.AcademicPeriodStatus;
import com.example.siga.entity.Career;
import com.example.siga.entity.Enrollment;
import com.example.siga.entity.EnrollmentStatus;
import com.example.siga.entity.Grade;
import com.example.siga.entity.Section;
import com.example.siga.entity.Student;
import com.example.siga.entity.StudentStatus;
import com.example.siga.entity.StudyPlan;
import com.example.siga.entity.StudyPlanStatus;
import com.example.siga.entity.Subject;
import com.example.siga.entity.Teacher;
import com.example.siga.entity.TeacherStatus;
import com.example.siga.exception.BusinessException;
import com.example.siga.exception.ResourceNotFoundException;
import com.example.siga.mapper.GradeMapper;
import com.example.siga.repository.EnrollmentRepository;
import com.example.siga.repository.GradeRepository;
import com.example.siga.repository.SectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GradeServiceImplTest {

    @Mock
    private GradeRepository gradeRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private SectionRepository sectionRepository;

    private GradeServiceImpl service;

    private AcademicPeriod openPeriod;
    private AcademicPeriod closedPeriod;
    private Teacher owner;
    private Section section;
    private Student student;
    private Subject subject;
    private Enrollment enrollment;

    @BeforeEach
    void setUp() {
        service = new GradeServiceImpl(
                gradeRepository, enrollmentRepository, sectionRepository, new GradeMapper());

        openPeriod = AcademicPeriod.builder()
                .id(10L).code("2026-1").year(2026).semester(1)
                .status(AcademicPeriodStatus.ABIERTO)
                .build();

        closedPeriod = AcademicPeriod.builder()
                .id(11L).code("2025-2").year(2025).semester(2)
                .status(AcademicPeriodStatus.CERRADO)
                .build();

        Career career = Career.builder().id(1L).code("10450").name("Tecnico Analista Programador").build();

        StudyPlan plan = StudyPlan.builder()
                .id(2L).identifier("P1").career(career)
                .status(StudyPlanStatus.VIGENTE)
                .build();

        subject = Subject.builder()
                .id(3L).code("TCS101").name("Algoritmos").semester(1)
                .telTheory(2).telExercises(2).telLab(0).sct(5)
                .studyPlan(plan)
                .build();

        owner = Teacher.builder()
                .id(20L).run("11111111-1").fullName("Ana Perez").email("ana.perez@ipt.cl")
                .professionalTitle("Ingeniera").academicDegree("Magister")
                .status(TeacherStatus.ACTIVO)
                .build();

        section = Section.builder()
                .id(40L).subject(subject).academicPeriod(openPeriod).teacher(owner).capacity(30)
                .build();

        student = Student.builder()
                .id(30L).run("22222222-2").fullName("Juan Soto").email("juan.soto@ipt.cl")
                .status(StudentStatus.REGULAR).career(career).studyPlan(plan)
                .build();

        enrollment = Enrollment.builder()
                .id(50L).student(student).subject(subject).section(section).academicPeriod(openPeriod)
                .enrollmentDate(LocalDateTime.now())
                .status(EnrollmentStatus.INSCRITA)
                .build();
    }

    private GradeRequestDTO request(String value) {
        return GradeRequestDTO.builder()
                .enrollmentId(50L)
                .value(new BigDecimal(value))
                .build();
    }

    private Grade gradeOf(Long id, Enrollment target, String value) {
        return Grade.builder()
                .id(id)
                .enrollment(target)
                .value(new BigDecimal(value))
                .recordedAt(LocalDateTime.now())
                .build();
    }

    // ---------- create ----------

    @Test
    @DisplayName("create: registra la nota y la deja APROBADO desde 4.0")
    void createShouldRecordApprovedGrade() {
        when(enrollmentRepository.findById(50L)).thenReturn(Optional.of(enrollment));
        when(gradeRepository.existsByEnrollmentId(50L)).thenReturn(false);
        when(gradeRepository.save(any(Grade.class))).thenAnswer(inv -> {
            Grade saved = inv.getArgument(0);
            saved.setId(60L);
            return saved;
        });

        GradeResponseDTO result = service.create(20L, request("4.0"));

        assertThat(result.getId()).isEqualTo(60L);
        assertThat(result.getEnrollmentId()).isEqualTo(50L);
        assertThat(result.getStudentRun()).isEqualTo("22222222-2");
        assertThat(result.getSubjectCode()).isEqualTo("TCS101");
        assertThat(result.getAcademicPeriodCode()).isEqualTo("2026-1");
        assertThat(result.getValue()).isEqualByComparingTo("4.0");
        assertThat(result.getResult()).isEqualTo(AcademicHistoryResult.APROBADO);
        assertThat(result.getRecordedAt()).isNotNull();
        assertThat(result.getUpdatedAt()).isNull();
    }

    @Test
    @DisplayName("create: bajo 4.0 queda REPROBADO")
    void createShouldRecordFailedGrade() {
        when(enrollmentRepository.findById(50L)).thenReturn(Optional.of(enrollment));
        when(gradeRepository.existsByEnrollmentId(50L)).thenReturn(false);
        when(gradeRepository.save(any(Grade.class))).thenAnswer(inv -> inv.getArgument(0));

        GradeResponseDTO result = service.create(20L, request("3.9"));

        assertThat(result.getResult()).isEqualTo(AcademicHistoryResult.REPROBADO);
    }

    @Test
    @DisplayName("create: la nota se guarda con un decimal")
    void createShouldRoundToOneDecimal() {
        when(enrollmentRepository.findById(50L)).thenReturn(Optional.of(enrollment));
        when(gradeRepository.existsByEnrollmentId(50L)).thenReturn(false);
        when(gradeRepository.save(any(Grade.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(20L, request("5.55"));

        ArgumentCaptor<Grade> captor = ArgumentCaptor.forClass(Grade.class);
        verify(gradeRepository).save(captor.capture());
        assertThat(captor.getValue().getValue()).isEqualByComparingTo("5.6");
        assertThat(captor.getValue().getValue().scale()).isEqualTo(1);
    }

    @Test
    @DisplayName("create: solo el docente responsable de la seccion califica")
    void createShouldRejectAnotherTeacher() {
        when(enrollmentRepository.findById(50L)).thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> service.create(99L, request("5.0")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Teacher 99 is not responsible for the section of subject 'TCS101'");

        verify(gradeRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: no se califica con el periodo CERRADO")
    void createShouldRejectClosedPeriod() {
        enrollment.setAcademicPeriod(closedPeriod);
        when(enrollmentRepository.findById(50L)).thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> service.create(20L, request("5.0")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("is CERRADO: grades cannot be changed");

        verify(gradeRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: una inscripcion anulada no se califica")
    void createShouldRejectCancelledEnrollment() {
        enrollment.setStatus(EnrollmentStatus.ANULADA);
        when(enrollmentRepository.findById(50L)).thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> service.create(20L, request("5.0")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Enrollment 50 is ANULADA and cannot be graded");

        verify(gradeRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: una sola calificacion final por inscripcion")
    void createShouldRejectSecondGrade() {
        when(enrollmentRepository.findById(50L)).thenReturn(Optional.of(enrollment));
        when(gradeRepository.existsByEnrollmentId(50L)).thenReturn(true);

        assertThatThrownBy(() -> service.create(20L, request("5.0")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already has a final grade");

        verify(gradeRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: rechaza una nota fuera de la escala 1.0 a 7.0")
    void createShouldRejectOutOfRangeGrade() {
        when(enrollmentRepository.findById(50L)).thenReturn(Optional.of(enrollment));
        when(gradeRepository.existsByEnrollmentId(50L)).thenReturn(false);

        assertThatThrownBy(() -> service.create(20L, request("7.1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Grade value must be between 1.0 and 7.0");

        assertThatThrownBy(() -> service.create(20L, request("0.9")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Grade value must be between 1.0 and 7.0");

        verify(gradeRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: rechaza una nota nula")
    void createShouldRejectNullGrade() {
        when(enrollmentRepository.findById(50L)).thenReturn(Optional.of(enrollment));
        when(gradeRepository.existsByEnrollmentId(50L)).thenReturn(false);

        GradeRequestDTO dto = GradeRequestDTO.builder().enrollmentId(50L).build();

        assertThatThrownBy(() -> service.create(20L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Grade value is required");

        verify(gradeRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: 404 si la inscripcion no existe")
    void createShouldFailWhenEnrollmentNotFound() {
        when(enrollmentRepository.findById(999L)).thenReturn(Optional.empty());

        GradeRequestDTO dto = GradeRequestDTO.builder()
                .enrollmentId(999L).value(new BigDecimal("5.0")).build();

        assertThatThrownBy(() -> service.create(20L, dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Enrollment not found with id: 999");
    }

    // ---------- update ----------

    @Test
    @DisplayName("update: modifica la nota mientras el periodo esta ABIERTO")
    void updateShouldChangeGrade() {
        Grade grade = gradeOf(60L, enrollment, "3.0");
        when(gradeRepository.findById(60L)).thenReturn(Optional.of(grade));
        when(gradeRepository.save(any(Grade.class))).thenAnswer(inv -> inv.getArgument(0));

        GradeResponseDTO result = service.update(20L, 60L, request("6.0"));

        assertThat(result.getValue()).isEqualByComparingTo("6.0");
        assertThat(result.getResult()).isEqualTo(AcademicHistoryResult.APROBADO);
        assertThat(result.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("update: la nota no se traspasa a otra inscripcion")
    void updateShouldRejectAnotherEnrollment() {
        Grade grade = gradeOf(60L, enrollment, "3.0");
        when(gradeRepository.findById(60L)).thenReturn(Optional.of(grade));

        GradeRequestDTO dto = GradeRequestDTO.builder()
                .enrollmentId(51L).value(new BigDecimal("6.0")).build();

        assertThatThrownBy(() -> service.update(20L, 60L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("belongs to enrollment 50 and cannot be moved to enrollment 51");

        verify(gradeRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: solo el docente responsable modifica la nota")
    void updateShouldRejectAnotherTeacher() {
        Grade grade = gradeOf(60L, enrollment, "3.0");
        when(gradeRepository.findById(60L)).thenReturn(Optional.of(grade));

        assertThatThrownBy(() -> service.update(99L, 60L, request("6.0")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("is not responsible for the section of subject 'TCS101'");

        verify(gradeRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: con el periodo CERRADO la nota ya no se modifica")
    void updateShouldRejectClosedPeriod() {
        enrollment.setAcademicPeriod(closedPeriod);
        Grade grade = gradeOf(60L, enrollment, "3.0");
        when(gradeRepository.findById(60L)).thenReturn(Optional.of(grade));

        assertThatThrownBy(() -> service.update(20L, 60L, request("6.0")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("is CERRADO: grades cannot be changed");

        verify(gradeRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: 404 si la calificacion no existe")
    void updateShouldFailWhenNotFound() {
        when(gradeRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(20L, 999L, request("6.0")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Grade not found with id: 999");
    }

    // ---------- findBySection ----------

    @Test
    @DisplayName("findBySection: el acta incluye las inscripciones todavia sin nota")
    void findBySectionShouldListGradedAndPending() {
        Student other = Student.builder()
                .id(31L).run("33333333-3").fullName("Maria Lopez").email("maria.lopez@ipt.cl")
                .status(StudentStatus.REGULAR).career(student.getCareer()).studyPlan(student.getStudyPlan())
                .build();

        Enrollment pending = Enrollment.builder()
                .id(51L).student(other).subject(subject).section(section).academicPeriod(openPeriod)
                .enrollmentDate(LocalDateTime.now())
                .status(EnrollmentStatus.INSCRITA)
                .build();

        when(sectionRepository.findById(40L)).thenReturn(Optional.of(section));
        when(gradeRepository.findByEnrollmentSectionId(40L))
                .thenReturn(List.of(gradeOf(60L, enrollment, "5.0")));
        when(enrollmentRepository.findBySectionIdAndStatus(40L, EnrollmentStatus.INSCRITA))
                .thenReturn(List.of(enrollment, pending));

        List<GradeResponseDTO> result = service.findBySection(20L, 40L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(60L);
        assertThat(result.get(0).getValue()).isEqualByComparingTo("5.0");
        assertThat(result.get(1).getId()).isNull();
        assertThat(result.get(1).getValue()).isNull();
        assertThat(result.get(1).getResult()).isNull();
        assertThat(result.get(1).getStudentFullName()).isEqualTo("Maria Lopez");
        assertThat(result.get(1).getEnrollmentId()).isEqualTo(51L);
    }

    @Test
    @DisplayName("findBySection: el ADMIN consulta el acta sin ser el docente responsable")
    void findBySectionShouldAllowAdmin() {
        when(sectionRepository.findById(40L)).thenReturn(Optional.of(section));
        when(gradeRepository.findByEnrollmentSectionId(40L)).thenReturn(List.of());
        when(enrollmentRepository.findBySectionIdAndStatus(40L, EnrollmentStatus.INSCRITA))
                .thenReturn(List.of(enrollment));

        List<GradeResponseDTO> result = service.findBySection(null, 40L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getValue()).isNull();
    }

    @Test
    @DisplayName("findBySection: un docente no ve el acta de otra seccion")
    void findBySectionShouldRejectAnotherTeacher() {
        when(sectionRepository.findById(40L)).thenReturn(Optional.of(section));

        assertThatThrownBy(() -> service.findBySection(99L, 40L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("is not responsible for the section of subject 'TCS101'");
    }

    @Test
    @DisplayName("findBySection: 404 si la seccion no existe")
    void findBySectionShouldFailWhenSectionNotFound() {
        when(sectionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findBySection(20L, 999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Section not found with id: 999");
    }
}
