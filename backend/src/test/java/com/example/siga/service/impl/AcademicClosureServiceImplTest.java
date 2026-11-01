package com.example.siga.service.impl;

import com.example.siga.dto.AcademicClosureResponseDTO;
import com.example.siga.dto.ClosureReadinessResponseDTO;
import com.example.siga.entity.AcademicHistory;
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
import com.example.siga.repository.AcademicHistoryRepository;
import com.example.siga.repository.AcademicPeriodRepository;
import com.example.siga.repository.EnrollmentRepository;
import com.example.siga.repository.GradeRepository;
import com.example.siga.mapper.SectionMapper;
import com.example.siga.repository.SectionRepository;
import com.example.siga.repository.StudentRepository;
import com.example.siga.repository.SubjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
class AcademicClosureServiceImplTest {

    @Mock
    private AcademicPeriodRepository academicPeriodRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private GradeRepository gradeRepository;

    @Mock
    private AcademicHistoryRepository academicHistoryRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private SectionRepository sectionRepository;

    private AcademicClosureServiceImpl service;

    private AcademicPeriod openPeriod;
    private AcademicPeriod closedPeriod;
    private StudyPlan plan;
    private Student student;
    private Subject algoritmos;
    private Subject programacion;
    private Section section;
    private Section otherSection;

    @BeforeEach
    void setUp() {
        service = new AcademicClosureServiceImpl(
                academicPeriodRepository, enrollmentRepository, gradeRepository,
                academicHistoryRepository, studentRepository, subjectRepository,
                sectionRepository, new SectionMapper());

        openPeriod = AcademicPeriod.builder()
                .id(10L).code("2026-1").year(2026).semester(1)
                .status(AcademicPeriodStatus.ABIERTO)
                .build();

        closedPeriod = AcademicPeriod.builder()
                .id(11L).code("2025-2").year(2025).semester(2)
                .status(AcademicPeriodStatus.CERRADO)
                .build();

        Career career = Career.builder().id(1L).code("10450").name("Tecnico Analista Programador").build();

        plan = StudyPlan.builder()
                .id(2L).identifier("2021.3").name("Plan 2021").career(career)
                .status(StudyPlanStatus.VIGENTE)
                .build();

        algoritmos = Subject.builder()
                .id(3L).code("TCS101").name("Algoritmos").semester(1)
                .telTheory(2).telExercises(2).telLab(0).sct(5).studyPlan(plan)
                .build();

        programacion = Subject.builder()
                .id(4L).code("TCS102").name("Programacion").semester(1)
                .telTheory(2).telExercises(2).telLab(0).sct(5).studyPlan(plan)
                .build();

        Teacher teacher = Teacher.builder()
                .id(20L).run("11111111-1").fullName("Ana Perez").email("ana.perez@ipt.cl")
                .professionalTitle("Ingeniera").academicDegree("Magister")
                .status(TeacherStatus.ACTIVO)
                .build();

        section = Section.builder()
                .id(40L).subject(algoritmos).academicPeriod(openPeriod).teacher(teacher).capacity(30)
                .build();

        otherSection = Section.builder()
                .id(41L).subject(programacion).academicPeriod(openPeriod).teacher(teacher).capacity(30)
                .build();

        student = Student.builder()
                .id(30L).run("22222222-2").fullName("Juan Soto").email("juan.soto@ipt.cl")
                .status(StudentStatus.REGULAR).career(career).studyPlan(plan)
                .build();
    }

    private Enrollment enrollmentOf(Long id, Student owner, Subject subject) {
        return Enrollment.builder()
                .id(id).student(owner).subject(subject).section(section).academicPeriod(openPeriod)
                .enrollmentDate(LocalDateTime.now())
                .status(EnrollmentStatus.INSCRITA)
                .build();
    }

    private Enrollment enrollmentIn(Long id, Section target, Subject subject) {
        Enrollment enrollment = enrollmentOf(id, student, subject);
        enrollment.setSection(target);
        return enrollment;
    }

    private Grade gradeOf(Enrollment enrollment, String value) {
        return Grade.builder()
                .id(enrollment.getId() + 100)
                .enrollment(enrollment)
                .value(new BigDecimal(value))
                .recordedAt(LocalDateTime.now())
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<AcademicHistory> capturedHistory() {
        ArgumentCaptor<List<AcademicHistory>> captor = ArgumentCaptor.forClass(List.class);
        verify(academicHistoryRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    // ---------- consolidacion del historial ----------

    @Test
    @DisplayName("close: copia cada inscripcion al historial y deja el periodo CERRADO")
    void closeShouldConsolidateHistoryAndClosePeriod() {
        Enrollment approved = enrollmentOf(50L, student, algoritmos);
        Enrollment failed = enrollmentOf(51L, student, programacion);

        when(academicPeriodRepository.findById(10L)).thenReturn(Optional.of(openPeriod));
        when(enrollmentRepository.findByAcademicPeriodIdAndStatus(10L, EnrollmentStatus.INSCRITA))
                .thenReturn(List.of(approved, failed));
        when(gradeRepository.findByEnrollmentAcademicPeriodId(10L))
                .thenReturn(List.of(gradeOf(approved, "5.5"), gradeOf(failed, "2.0")));
        when(academicHistoryRepository.countByStudentIdAndSubjectCodeAndResult(
                30L, "TCS102", AcademicHistoryResult.REPROBADO)).thenReturn(0L);

        AcademicClosureResponseDTO result = service.close(10L);

        assertThat(result.getAcademicPeriodCode()).isEqualTo("2026-1");
        assertThat(result.getStatus()).isEqualTo(AcademicPeriodStatus.CERRADO);
        assertThat(result.getConsolidatedEnrollments()).isEqualTo(2);
        assertThat(result.getApprovedCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(result.getGraduatedStudentRuns()).isEmpty();
        assertThat(result.getEliminatedStudentRuns()).isEmpty();

        List<AcademicHistory> history = capturedHistory();
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getSubjectCode()).isEqualTo("TCS101");
        assertThat(history.get(0).getSubjectName()).isEqualTo("Algoritmos");
        assertThat(history.get(0).getGrade()).isEqualByComparingTo("5.5");
        assertThat(history.get(0).getResult()).isEqualTo(AcademicHistoryResult.APROBADO);
        assertThat(history.get(0).getAcademicPeriod().getCode()).isEqualTo("2026-1");
        assertThat(history.get(1).getResult()).isEqualTo(AcademicHistoryResult.REPROBADO);

        assertThat(openPeriod.getStatus()).isEqualTo(AcademicPeriodStatus.CERRADO);
        verify(academicPeriodRepository).save(openPeriod);
        verify(studentRepository, never()).save(any());
    }

    @Test
    @DisplayName("close: un periodo sin inscripciones se cierra igual")
    void closeShouldCloseEmptyPeriod() {
        when(academicPeriodRepository.findById(10L)).thenReturn(Optional.of(openPeriod));
        when(enrollmentRepository.findByAcademicPeriodIdAndStatus(10L, EnrollmentStatus.INSCRITA))
                .thenReturn(List.of());
        when(gradeRepository.findByEnrollmentAcademicPeriodId(10L)).thenReturn(List.of());

        AcademicClosureResponseDTO result = service.close(10L);

        assertThat(result.getConsolidatedEnrollments()).isZero();
        assertThat(result.getStatus()).isEqualTo(AcademicPeriodStatus.CERRADO);
        assertThat(capturedHistory()).isEmpty();
    }

    @Test
    @DisplayName("close: rechaza el cierre si faltan notas e informa las asignaturas")
    void closeShouldRejectPendingGrades() {
        Enrollment graded = enrollmentOf(50L, student, algoritmos);
        Enrollment pending = enrollmentOf(51L, student, programacion);

        when(academicPeriodRepository.findById(10L)).thenReturn(Optional.of(openPeriod));
        when(enrollmentRepository.findByAcademicPeriodIdAndStatus(10L, EnrollmentStatus.INSCRITA))
                .thenReturn(List.of(graded, pending));
        when(gradeRepository.findByEnrollmentAcademicPeriodId(10L))
                .thenReturn(List.of(gradeOf(graded, "5.0")));

        assertThatThrownBy(() -> service.close(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot be closed: pending grades in sections of subjects [TCS102]");

        assertThat(openPeriod.getStatus()).isEqualTo(AcademicPeriodStatus.ABIERTO);
        verify(academicHistoryRepository, never()).saveAll(any());
        verify(academicPeriodRepository, never()).save(any());
    }

    @Test
    @DisplayName("close: una inscripcion anulada no entra al historial ni bloquea el cierre")
    void closeShouldIgnoreCancelledEnrollments() {
        Enrollment approved = enrollmentOf(50L, student, algoritmos);
        Enrollment cancelled = enrollmentOf(51L, student, programacion);
        cancelled.setStatus(EnrollmentStatus.ANULADA);

        when(academicPeriodRepository.findById(10L)).thenReturn(Optional.of(openPeriod));
        // El repositorio solo devuelve las INSCRITA
        when(enrollmentRepository.findByAcademicPeriodIdAndStatus(10L, EnrollmentStatus.INSCRITA))
                .thenReturn(List.of(approved));
        when(gradeRepository.findByEnrollmentAcademicPeriodId(10L))
                .thenReturn(List.of(gradeOf(approved, "4.0")));
        when(subjectRepository.findByStudyPlanId(2L)).thenReturn(List.of(algoritmos, programacion));

        AcademicClosureResponseDTO result = service.close(10L);

        assertThat(result.getConsolidatedEnrollments()).isEqualTo(1);
        assertThat(capturedHistory()).hasSize(1);
    }

    @Test
    @DisplayName("close: un periodo CERRADO no se cierra de nuevo")
    void closeShouldRejectClosedPeriod() {
        when(academicPeriodRepository.findById(11L)).thenReturn(Optional.of(closedPeriod));

        assertThatThrownBy(() -> service.close(11L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("is already CERRADO and cannot be closed again");

        verify(academicHistoryRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("close: 404 si el periodo no existe")
    void closeShouldFailWhenPeriodNotFound() {
        when(academicPeriodRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.close(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Academic period not found with id: 999");
    }

    // ---------- estado del estudiante ----------

    @Test
    @DisplayName("close: aprobar todas las asignaturas del plan deja al estudiante EGRESADO")
    void closeShouldGraduateStudent() {
        Enrollment first = enrollmentOf(50L, student, algoritmos);
        Enrollment second = enrollmentOf(51L, student, programacion);

        when(academicPeriodRepository.findById(10L)).thenReturn(Optional.of(openPeriod));
        when(enrollmentRepository.findByAcademicPeriodIdAndStatus(10L, EnrollmentStatus.INSCRITA))
                .thenReturn(List.of(first, second));
        when(gradeRepository.findByEnrollmentAcademicPeriodId(10L))
                .thenReturn(List.of(gradeOf(first, "6.0"), gradeOf(second, "4.0")));
        when(subjectRepository.findByStudyPlanId(2L)).thenReturn(List.of(algoritmos, programacion));

        AcademicClosureResponseDTO result = service.close(10L);

        assertThat(student.getStatus()).isEqualTo(StudentStatus.EGRESADO);
        assertThat(result.getGraduatedStudentRuns()).containsExactly("22222222-2");
        verify(studentRepository).save(student);
    }

    @Test
    @DisplayName("close: el historial previo cuenta para egresar")
    void closeShouldGraduateUsingPreviousHistory() {
        Enrollment last = enrollmentOf(50L, student, programacion);

        when(academicPeriodRepository.findById(10L)).thenReturn(Optional.of(openPeriod));
        when(enrollmentRepository.findByAcademicPeriodIdAndStatus(10L, EnrollmentStatus.INSCRITA))
                .thenReturn(List.of(last));
        when(gradeRepository.findByEnrollmentAcademicPeriodId(10L))
                .thenReturn(List.of(gradeOf(last, "5.0")));
        when(academicHistoryRepository.findByStudentIdAndResult(30L, AcademicHistoryResult.APROBADO))
                .thenReturn(List.of(AcademicHistory.builder()
                        .id(1L).student(student).subjectCode("TCS101").subjectName("Algoritmos")
                        .academicPeriod(closedPeriod).grade(new BigDecimal("5.0"))
                        .result(AcademicHistoryResult.APROBADO)
                        .build()));
        when(subjectRepository.findByStudyPlanId(2L)).thenReturn(List.of(algoritmos, programacion));

        service.close(10L);

        assertThat(student.getStatus()).isEqualTo(StudentStatus.EGRESADO);
    }

    @Test
    @DisplayName("close: con una asignatura del plan pendiente el estudiante sigue REGULAR")
    void closeShouldKeepStudentRegularWhenPlanIsNotComplete() {
        Enrollment first = enrollmentOf(50L, student, algoritmos);

        when(academicPeriodRepository.findById(10L)).thenReturn(Optional.of(openPeriod));
        when(enrollmentRepository.findByAcademicPeriodIdAndStatus(10L, EnrollmentStatus.INSCRITA))
                .thenReturn(List.of(first));
        when(gradeRepository.findByEnrollmentAcademicPeriodId(10L))
                .thenReturn(List.of(gradeOf(first, "6.0")));
        when(subjectRepository.findByStudyPlanId(2L)).thenReturn(List.of(algoritmos, programacion));

        AcademicClosureResponseDTO result = service.close(10L);

        assertThat(student.getStatus()).isEqualTo(StudentStatus.REGULAR);
        assertThat(result.getGraduatedStudentRuns()).isEmpty();
        verify(studentRepository, never()).save(any());
    }

    @Test
    @DisplayName("close: reprobar la misma asignatura por segunda vez deja al estudiante ELIMINADO")
    void closeShouldEliminateStudentOnSecondFailure() {
        Enrollment failed = enrollmentOf(50L, student, algoritmos);

        when(academicPeriodRepository.findById(10L)).thenReturn(Optional.of(openPeriod));
        when(enrollmentRepository.findByAcademicPeriodIdAndStatus(10L, EnrollmentStatus.INSCRITA))
                .thenReturn(List.of(failed));
        when(gradeRepository.findByEnrollmentAcademicPeriodId(10L))
                .thenReturn(List.of(gradeOf(failed, "3.0")));
        // Ya la habia reprobado una vez
        when(academicHistoryRepository.countByStudentIdAndSubjectCodeAndResult(
                30L, "TCS101", AcademicHistoryResult.REPROBADO)).thenReturn(1L);

        AcademicClosureResponseDTO result = service.close(10L);

        assertThat(student.getStatus()).isEqualTo(StudentStatus.ELIMINADO);
        assertThat(result.getEliminatedStudentRuns()).containsExactly("22222222-2");
        assertThat(result.getGraduatedStudentRuns()).isEmpty();
        verify(studentRepository).save(student);
        // El historial se guarda igual: la reprobacion queda registrada
        assertThat(capturedHistory()).hasSize(1);
    }

    @Test
    @DisplayName("close: la primera reprobacion no elimina al estudiante")
    void closeShouldNotEliminateOnFirstFailure() {
        Enrollment failed = enrollmentOf(50L, student, algoritmos);

        when(academicPeriodRepository.findById(10L)).thenReturn(Optional.of(openPeriod));
        when(enrollmentRepository.findByAcademicPeriodIdAndStatus(10L, EnrollmentStatus.INSCRITA))
                .thenReturn(List.of(failed));
        when(gradeRepository.findByEnrollmentAcademicPeriodId(10L))
                .thenReturn(List.of(gradeOf(failed, "3.0")));
        when(academicHistoryRepository.countByStudentIdAndSubjectCodeAndResult(
                30L, "TCS101", AcademicHistoryResult.REPROBADO)).thenReturn(0L);

        service.close(10L);

        assertThat(student.getStatus()).isEqualTo(StudentStatus.REGULAR);
        verify(studentRepository, never()).save(any());
    }

    @Test
    @DisplayName("close: la eliminacion tiene prioridad sobre el egreso")
    void closeShouldPreferEliminationOverGraduation() {
        Enrollment approved = enrollmentOf(50L, student, algoritmos);
        Enrollment failed = enrollmentOf(51L, student, programacion);

        when(academicPeriodRepository.findById(10L)).thenReturn(Optional.of(openPeriod));
        when(enrollmentRepository.findByAcademicPeriodIdAndStatus(10L, EnrollmentStatus.INSCRITA))
                .thenReturn(List.of(approved, failed));
        when(gradeRepository.findByEnrollmentAcademicPeriodId(10L))
                .thenReturn(List.of(gradeOf(approved, "6.0"), gradeOf(failed, "2.5")));
        when(academicHistoryRepository.countByStudentIdAndSubjectCodeAndResult(
                30L, "TCS102", AcademicHistoryResult.REPROBADO)).thenReturn(1L);

        AcademicClosureResponseDTO result = service.close(10L);

        assertThat(student.getStatus()).isEqualTo(StudentStatus.ELIMINADO);
        assertThat(result.getGraduatedStudentRuns()).isEmpty();
        verify(subjectRepository, never()).findByStudyPlanId(anyLong());
    }

    @Test
    @DisplayName("close: un estudiante que ya no es REGULAR no se reevalua")
    void closeShouldSkipNonRegularStudent() {
        student.setStatus(StudentStatus.EGRESADO);
        Enrollment enrollment = enrollmentOf(50L, student, algoritmos);

        when(academicPeriodRepository.findById(10L)).thenReturn(Optional.of(openPeriod));
        when(enrollmentRepository.findByAcademicPeriodIdAndStatus(10L, EnrollmentStatus.INSCRITA))
                .thenReturn(List.of(enrollment));
        when(gradeRepository.findByEnrollmentAcademicPeriodId(10L))
                .thenReturn(List.of(gradeOf(enrollment, "1.0")));

        service.close(10L);

        assertThat(student.getStatus()).isEqualTo(StudentStatus.EGRESADO);
        verify(studentRepository, never()).save(any());
        verify(academicHistoryRepository, never())
                .countByStudentIdAndSubjectCodeAndResult(anyLong(), anyString(), any());
        // El historial se consolida igual
        assertThat(capturedHistory()).hasSize(1);
    }

    @Test
    @DisplayName("close: cada estudiante del periodo se evalua por separado")
    void closeShouldEvaluateEachStudentIndependently() {
        Student other = Student.builder()
                .id(31L).run("33333333-3").fullName("Maria Lopez").email("maria.lopez@ipt.cl")
                .status(StudentStatus.REGULAR).career(student.getCareer()).studyPlan(plan)
                .build();

        Enrollment mine = enrollmentOf(50L, student, algoritmos);
        Enrollment theirs = enrollmentOf(51L, other, algoritmos);

        when(academicPeriodRepository.findById(10L)).thenReturn(Optional.of(openPeriod));
        when(enrollmentRepository.findByAcademicPeriodIdAndStatus(10L, EnrollmentStatus.INSCRITA))
                .thenReturn(new ArrayList<>(List.of(mine, theirs)));
        when(gradeRepository.findByEnrollmentAcademicPeriodId(10L))
                .thenReturn(List.of(gradeOf(mine, "4.0"), gradeOf(theirs, "2.0")));
        when(subjectRepository.findByStudyPlanId(2L)).thenReturn(List.of(algoritmos));
        when(academicHistoryRepository.countByStudentIdAndSubjectCodeAndResult(
                31L, "TCS101", AcademicHistoryResult.REPROBADO)).thenReturn(1L);

        AcademicClosureResponseDTO result = service.close(10L);

        assertThat(student.getStatus()).isEqualTo(StudentStatus.EGRESADO);
        assertThat(other.getStatus()).isEqualTo(StudentStatus.ELIMINADO);
        assertThat(result.getGraduatedStudentRuns()).containsExactly("22222222-2");
        assertThat(result.getEliminatedStudentRuns()).containsExactly("33333333-3");
        assertThat(result.getConsolidatedEnrollments()).isEqualTo(2);
    }

    @Test
    @DisplayName("close: un plan sin asignaturas no egresa a nadie")
    void closeShouldNotGraduateWithEmptyPlan() {
        Enrollment approved = enrollmentOf(50L, student, algoritmos);

        when(academicPeriodRepository.findById(10L)).thenReturn(Optional.of(openPeriod));
        when(enrollmentRepository.findByAcademicPeriodIdAndStatus(10L, EnrollmentStatus.INSCRITA))
                .thenReturn(List.of(approved));
        when(gradeRepository.findByEnrollmentAcademicPeriodId(10L))
                .thenReturn(List.of(gradeOf(approved, "7.0")));
        when(subjectRepository.findByStudyPlanId(2L)).thenReturn(List.of());

        AcademicClosureResponseDTO result = service.close(10L);

        assertThat(student.getStatus()).isEqualTo(StudentStatus.REGULAR);
        assertThat(result.getGraduatedStudentRuns()).isEmpty();
    }

    // ---------- diagnostico previo al cierre ----------

    @Test
    @DisplayName("readiness: con todas las notas registradas el periodo se puede cerrar")
    void readinessShouldAllowClosureWhenEverythingIsGraded() {
        Enrollment first = enrollmentIn(50L, section, algoritmos);
        Enrollment second = enrollmentIn(51L, otherSection, programacion);

        when(academicPeriodRepository.findById(10L)).thenReturn(Optional.of(openPeriod));
        when(enrollmentRepository.findByAcademicPeriodIdAndStatus(10L, EnrollmentStatus.INSCRITA))
                .thenReturn(List.of(first, second));
        when(gradeRepository.findByEnrollmentAcademicPeriodId(10L))
                .thenReturn(List.of(gradeOf(first, "5.5"), gradeOf(second, "3.0")));
        when(sectionRepository.findByAcademicPeriodId(10L)).thenReturn(List.of(section, otherSection));

        ClosureReadinessResponseDTO readiness = service.readiness(10L);

        assertThat(readiness.getAcademicPeriodId()).isEqualTo(10L);
        assertThat(readiness.getAcademicPeriodCode()).isEqualTo("2026-1");
        assertThat(readiness.getStatus()).isEqualTo(AcademicPeriodStatus.ABIERTO);
        assertThat(readiness.isCanClose()).isTrue();
        assertThat(readiness.getSectionCount()).isEqualTo(2);
        assertThat(readiness.getEnrolledCount()).isEqualTo(2);
        assertThat(readiness.getGradedCount()).isEqualTo(2);
        assertThat(readiness.getPendingCount()).isZero();
        assertThat(readiness.getPendingSubjectCodes()).isEmpty();

        assertThat(readiness.getSections()).hasSize(2);
        assertThat(readiness.getSections().get(0).getSection().getSubjectCode()).isEqualTo("TCS101");
        assertThat(readiness.getSections().get(0).getEnrolled()).isEqualTo(1);
        assertThat(readiness.getSections().get(0).getGraded()).isEqualTo(1);
        assertThat(readiness.getSections().get(0).getPending()).isZero();

        // Es un diagnostico: no toca nada
        verify(academicPeriodRepository, never()).save(any());
        verify(academicHistoryRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("readiness: una nota faltante bloquea el cierre y nombra su asignatura")
    void readinessShouldReportPendingGrades() {
        Enrollment graded = enrollmentIn(50L, section, algoritmos);
        Enrollment pending = enrollmentIn(51L, otherSection, programacion);
        Enrollment alsoPending = enrollmentIn(52L, otherSection, programacion);

        when(academicPeriodRepository.findById(10L)).thenReturn(Optional.of(openPeriod));
        when(enrollmentRepository.findByAcademicPeriodIdAndStatus(10L, EnrollmentStatus.INSCRITA))
                .thenReturn(List.of(graded, pending, alsoPending));
        when(gradeRepository.findByEnrollmentAcademicPeriodId(10L))
                .thenReturn(List.of(gradeOf(graded, "4.0")));
        when(sectionRepository.findByAcademicPeriodId(10L)).thenReturn(List.of(section, otherSection));

        ClosureReadinessResponseDTO readiness = service.readiness(10L);

        assertThat(readiness.isCanClose()).isFalse();
        assertThat(readiness.getEnrolledCount()).isEqualTo(3);
        assertThat(readiness.getGradedCount()).isEqualTo(1);
        assertThat(readiness.getPendingCount()).isEqualTo(2);
        // Dos inscripciones pendientes de la misma asignatura se nombran una vez,
        // igual que en el 409 del cierre
        assertThat(readiness.getPendingSubjectCodes()).containsExactly("TCS102");

        assertThat(readiness.getSections().get(1).getSection().getSubjectCode()).isEqualTo("TCS102");
        assertThat(readiness.getSections().get(1).getEnrolled()).isEqualTo(2);
        assertThat(readiness.getSections().get(1).getGraded()).isZero();
        assertThat(readiness.getSections().get(1).getPending()).isEqualTo(2);
    }

    @Test
    @DisplayName("readiness: el diagnostico coincide con las asignaturas que rechaza el cierre")
    void readinessShouldMatchTheClosureRejection() {
        Enrollment pending = enrollmentIn(50L, section, programacion);

        when(academicPeriodRepository.findById(10L)).thenReturn(Optional.of(openPeriod));
        when(enrollmentRepository.findByAcademicPeriodIdAndStatus(10L, EnrollmentStatus.INSCRITA))
                .thenReturn(List.of(pending));
        when(gradeRepository.findByEnrollmentAcademicPeriodId(10L)).thenReturn(List.of());
        when(sectionRepository.findByAcademicPeriodId(10L)).thenReturn(List.of(section));

        List<String> diagnosed = service.readiness(10L).getPendingSubjectCodes();

        assertThatThrownBy(() -> service.close(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(diagnosed.toString());
    }

    @Test
    @DisplayName("readiness: una seccion sin inscritos aparece en cero")
    void readinessShouldReportSectionsWithoutEnrollments() {
        when(academicPeriodRepository.findById(10L)).thenReturn(Optional.of(openPeriod));
        when(enrollmentRepository.findByAcademicPeriodIdAndStatus(10L, EnrollmentStatus.INSCRITA))
                .thenReturn(List.of());
        when(gradeRepository.findByEnrollmentAcademicPeriodId(10L)).thenReturn(List.of());
        when(sectionRepository.findByAcademicPeriodId(10L)).thenReturn(List.of(section));

        ClosureReadinessResponseDTO readiness = service.readiness(10L);

        assertThat(readiness.isCanClose()).isTrue();
        assertThat(readiness.getSectionCount()).isEqualTo(1);
        assertThat(readiness.getEnrolledCount()).isZero();
        assertThat(readiness.getSections().get(0).getEnrolled()).isZero();
        assertThat(readiness.getSections().get(0).getPending()).isZero();
    }

    @Test
    @DisplayName("readiness: un periodo CERRADO no se puede volver a cerrar")
    void readinessShouldRejectClosedPeriod() {
        when(academicPeriodRepository.findById(11L)).thenReturn(Optional.of(closedPeriod));
        when(enrollmentRepository.findByAcademicPeriodIdAndStatus(11L, EnrollmentStatus.INSCRITA))
                .thenReturn(List.of());
        when(gradeRepository.findByEnrollmentAcademicPeriodId(11L)).thenReturn(List.of());
        when(sectionRepository.findByAcademicPeriodId(11L)).thenReturn(List.of());

        ClosureReadinessResponseDTO readiness = service.readiness(11L);

        assertThat(readiness.getStatus()).isEqualTo(AcademicPeriodStatus.CERRADO);
        assertThat(readiness.isCanClose()).isFalse();
        assertThat(readiness.getPendingCount()).isZero();
    }

    @Test
    @DisplayName("readiness: 404 si el periodo no existe")
    void readinessShouldFailWhenPeriodDoesNotExist() {
        when(academicPeriodRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.readiness(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Academic period not found with id: 999");
    }
}
