package com.example.siga.service.impl;

import com.example.siga.dto.AvailableSectionResponseDTO;
import com.example.siga.dto.EnrollmentRequestDTO;
import com.example.siga.dto.EnrollmentResponseDTO;
import com.example.siga.entity.AcademicHistory;
import com.example.siga.entity.AcademicHistoryResult;
import com.example.siga.entity.AcademicPeriod;
import com.example.siga.entity.AcademicPeriodStatus;
import com.example.siga.entity.Career;
import com.example.siga.entity.CareerStatus;
import com.example.siga.entity.Enrollment;
import com.example.siga.entity.EnrollmentStatus;
import com.example.siga.entity.MatriculationStatus;
import com.example.siga.entity.Prerequisite;
import com.example.siga.entity.ScheduleBlock;
import com.example.siga.entity.ScheduleDay;
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
import com.example.siga.mapper.EnrollmentMapper;
import com.example.siga.repository.AcademicHistoryRepository;
import com.example.siga.repository.AcademicPeriodRepository;
import com.example.siga.repository.EnrollmentRepository;
import com.example.siga.repository.GradeRepository;
import com.example.siga.repository.PrerequisiteRepository;
import com.example.siga.repository.SectionRepository;
import com.example.siga.repository.StudentPeriodStatusRepository;
import com.example.siga.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceImplTest {

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private AcademicPeriodRepository academicPeriodRepository;

    @Mock
    private SectionRepository sectionRepository;

    @Mock
    private PrerequisiteRepository prerequisiteRepository;

    @Mock
    private AcademicHistoryRepository academicHistoryRepository;

    @Mock
    private StudentPeriodStatusRepository studentPeriodStatusRepository;

    @Mock
    private GradeRepository gradeRepository;

    private EnrollmentServiceImpl service;

    private StudyPlan plan;
    private StudyPlan otherPlan;
    private Student student;
    private AcademicPeriod openPeriod;
    private AcademicPeriod closedPeriod;
    private Teacher teacher;

    private Subject tap101;   // semestre 1
    private Subject tap102;   // semestre 1, prerrequisito de tap201
    private Subject tap103;   // semestre 1
    private Subject tap201;   // semestre 2, exige tap102

    private Section sec101;   // L1 L2 M1, capacidad 30
    private Section sec102;   // M2 M3 W1, capacidad 2
    private Section sec103;   // W2 W3 J1, capacidad 30
    private Section sec201;   // J2 J3 V1, capacidad 30

    @BeforeEach
    void setUp() {
        service = new EnrollmentServiceImpl(
                enrollmentRepository, studentRepository, academicPeriodRepository, sectionRepository,
                prerequisiteRepository, academicHistoryRepository, studentPeriodStatusRepository,
                gradeRepository, new EnrollmentMapper());

        Career career = Career.builder()
                .id(1L).code("10450").name("Tecnico Analista Programador")
                .description("Carrera tecnica").status(CareerStatus.ACTIVA).durationSemesters(4)
                .build();

        plan = StudyPlan.builder()
                .id(50L).identifier("2021.3").name("Plan 2021")
                .status(StudyPlanStatus.VIGENTE).career(career)
                .build();

        otherPlan = StudyPlan.builder()
                .id(51L).identifier("2022.3").name("Plan 2022")
                .status(StudyPlanStatus.NO_VIGENTE).career(career)
                .build();

        student = Student.builder()
                .id(30L).run("12345678-9").fullName("Pedro Soto").email("pedro.soto@ipt.cl")
                .status(StudentStatus.REGULAR).career(career).studyPlan(plan)
                .build();

        openPeriod = AcademicPeriod.builder()
                .id(10L).code("2026-1").year(2026).semester(1)
                .status(AcademicPeriodStatus.ABIERTO)
                .build();

        closedPeriod = AcademicPeriod.builder()
                .id(11L).code("2025-2").year(2025).semester(2)
                .status(AcademicPeriodStatus.CERRADO)
                .build();

        teacher = Teacher.builder()
                .id(20L).run("98765432-1").fullName("Ana Perez").email("ana.perez@ipt.cl")
                .professionalTitle("Ingeniera").academicDegree("Magister")
                .status(TeacherStatus.ACTIVO)
                .build();

        tap101 = subject(101L, "TAP101", "Programacion I", 1, plan);
        tap102 = subject(102L, "TAP102", "Matematica I", 1, plan);
        tap103 = subject(103L, "TAP103", "Ingles I", 1, plan);
        tap201 = subject(104L, "TAP201", "Programacion II", 2, plan);

        sec101 = section(201L, tap101, openPeriod, 30, blocks(ScheduleDay.L, 1, ScheduleDay.L, 2, ScheduleDay.M, 1));
        sec102 = section(202L, tap102, openPeriod, 2, blocks(ScheduleDay.M, 2, ScheduleDay.M, 3, ScheduleDay.W, 1));
        sec103 = section(203L, tap103, openPeriod, 30, blocks(ScheduleDay.W, 2, ScheduleDay.W, 3, ScheduleDay.J, 1));
        sec201 = section(204L, tap201, openPeriod, 30, blocks(ScheduleDay.J, 2, ScheduleDay.J, 3, ScheduleDay.V, 1));

        stubCommon();
    }

    private Subject subject(Long id, String code, String name, int semester, StudyPlan studyPlan) {
        return Subject.builder()
                .id(id).code(code).name(name).semester(semester)
                .telTheory(2).telExercises(2).telLab(2).sct(5).studyPlan(studyPlan)
                .build();
    }

    private Section section(Long id, Subject subject, AcademicPeriod period, int capacity, List<ScheduleBlock> blocks) {
        Section section = Section.builder()
                .id(id).subject(subject).academicPeriod(period).teacher(teacher)
                .capacity(capacity).scheduleBlocks(new ArrayList<>())
                .build();
        section.replaceScheduleBlocks(blocks);
        return section;
    }

    private List<ScheduleBlock> blocks(Object... dayModulePairs) {
        List<ScheduleBlock> result = new ArrayList<>();
        for (int i = 0; i < dayModulePairs.length; i += 2) {
            result.add(ScheduleBlock.builder()
                    .day((ScheduleDay) dayModulePairs[i])
                    .module((Integer) dayModulePairs[i + 1])
                    .build());
        }
        return result;
    }

    private Enrollment enrollmentOf(Long id, Section section) {
        return Enrollment.builder()
                .id(id)
                .student(student)
                .subject(section.getSubject())
                .section(section)
                .academicPeriod(section.getAcademicPeriod())
                .enrollmentDate(LocalDateTime.now())
                .status(EnrollmentStatus.INSCRITA)
                .build();
    }

    private AcademicHistory approved(String code, String name) {
        return AcademicHistory.builder()
                .id(1L).student(student).subjectCode(code).subjectName(name)
                .academicPeriod(closedPeriod).grade(new BigDecimal("5.5"))
                .result(AcademicHistoryResult.APROBADO)
                .build();
    }

    /** Camino feliz: estudiante REGULAR, matriculado, periodo ABIERTO y sin historial. */
    private void stubCommon() {
        lenient().when(studentRepository.findById(30L)).thenReturn(Optional.of(student));
        lenient().when(academicPeriodRepository.findById(10L)).thenReturn(Optional.of(openPeriod));
        lenient().when(academicPeriodRepository.findById(11L)).thenReturn(Optional.of(closedPeriod));
        lenient().when(studentPeriodStatusRepository.existsByStudentIdAndAcademicPeriodIdAndEnrollmentStatus(
                30L, 10L, MatriculationStatus.MATRICULATED)).thenReturn(true);
        lenient().when(enrollmentRepository.findByStudentIdAndAcademicPeriodIdAndStatus(
                30L, 10L, EnrollmentStatus.INSCRITA)).thenReturn(List.of());
        lenient().when(academicHistoryRepository.findByStudentIdAndResult(
                30L, AcademicHistoryResult.APROBADO)).thenReturn(List.of());
        lenient().when(sectionRepository.findByAcademicPeriodId(10L))
                .thenReturn(List.of(sec101, sec102, sec103, sec201));
        lenient().when(prerequisiteRepository.findBySubjectId(101L)).thenReturn(List.of());
        lenient().when(prerequisiteRepository.findBySubjectId(102L)).thenReturn(List.of());
        lenient().when(prerequisiteRepository.findBySubjectId(103L)).thenReturn(List.of());
        lenient().when(prerequisiteRepository.findBySubjectId(104L)).thenReturn(List.of(
                Prerequisite.builder().id(1L).subject(tap201).prerequisiteSubject(tap102).build()));
        lenient().when(sectionRepository.findById(201L)).thenReturn(Optional.of(sec101));
        lenient().when(sectionRepository.findById(202L)).thenReturn(Optional.of(sec102));
        lenient().when(sectionRepository.findById(203L)).thenReturn(Optional.of(sec103));
        lenient().when(sectionRepository.findById(204L)).thenReturn(Optional.of(sec201));
        lenient().when(enrollmentRepository.countBySectionIdAndStatus(anyLong(), eq(EnrollmentStatus.INSCRITA)))
                .thenReturn(0L);
        lenient().when(enrollmentRepository.saveAll(anyList())).thenAnswer(inv -> {
            List<Enrollment> saved = inv.getArgument(0);
            long id = 1L;
            for (Enrollment enrollment : saved) {
                enrollment.setId(id++);
            }
            return saved;
        });
    }

    private EnrollmentRequestDTO request(Long... sectionIds) {
        return EnrollmentRequestDTO.builder()
                .academicPeriodId(10L)
                .sectionIds(Arrays.asList(sectionIds))
                .build();
    }

    // ---------- enroll: camino feliz ----------

    @Test
    @DisplayName("enroll: inscribe las 3 asignaturas seleccionadas")
    void enrollShouldRegisterSelectedSubjects() {
        List<EnrollmentResponseDTO> result = service.enroll(30L, request(201L, 202L, 203L));

        assertThat(result).hasSize(3);
        assertThat(result).allSatisfy(enrollment -> {
            assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.INSCRITA);
            assertThat(enrollment.getStudentRun()).isEqualTo("12345678-9");
            assertThat(enrollment.getAcademicPeriodCode()).isEqualTo("2026-1");
            assertThat(enrollment.getEnrollmentDate()).isNotNull();
        });
        assertThat(result).extracting(EnrollmentResponseDTO::getSubjectCode)
                .containsExactly("TAP101", "TAP102", "TAP103");
    }

    @Test
    @DisplayName("enroll: permite un semestre superior cuando no quedan pendientes anteriores")
    void enrollShouldAllowHigherSemesterWhenNoLowerPending() {
        when(academicHistoryRepository.findByStudentIdAndResult(30L, AcademicHistoryResult.APROBADO))
                .thenReturn(List.of(approved("TAP102", "Matematica I")));

        List<EnrollmentResponseDTO> result = service.enroll(30L, request(201L, 203L, 204L));

        assertThat(result).hasSize(3);
        assertThat(result).extracting(EnrollmentResponseDTO::getSubjectCode)
                .containsExactly("TAP101", "TAP103", "TAP201");
    }

    @Test
    @DisplayName("enroll: con menos de 3 disponibles puede inscribir solo las pendientes")
    void enrollShouldAllowFewerThanThreeWhenThatIsAllThereIs() {
        when(sectionRepository.findByAcademicPeriodId(10L)).thenReturn(List.of(sec101, sec102));

        assertThat(service.enroll(30L, request(201L, 202L))).hasSize(2);
    }

    // ---------- enroll: estado del estudiante y del periodo ----------

    @Test
    @DisplayName("enroll: el estudiante debe estar REGULAR")
    void enrollShouldRejectNonRegularStudent() {
        student.setStatus(StudentStatus.POSTERGACION);

        assertThatThrownBy(() -> service.enroll(30L, request(201L, 202L, 203L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("is POSTERGACION and cannot enroll subjects");

        verify(enrollmentRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("enroll: el periodo debe estar ABIERTO")
    void enrollShouldRejectClosedPeriod() {
        EnrollmentRequestDTO dto = request(201L, 202L, 203L);
        dto.setAcademicPeriodId(11L);

        assertThatThrownBy(() -> service.enroll(30L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("is CERRADO: enrollments cannot be changed");

        verify(enrollmentRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("enroll: el estudiante debe estar matriculado en el periodo")
    void enrollShouldRejectNotMatriculatedStudent() {
        when(studentPeriodStatusRepository.existsByStudentIdAndAcademicPeriodIdAndEnrollmentStatus(
                30L, 10L, MatriculationStatus.MATRICULATED)).thenReturn(false);

        assertThatThrownBy(() -> service.enroll(30L, request(201L, 202L, 203L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("is not MATRICULATED in academic period '2026-1'");

        verify(enrollmentRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("enroll: 404 si el estudiante no existe")
    void enrollShouldFailWhenStudentNotFound() {
        when(studentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enroll(999L, request(201L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Student not found with id: 999");
    }

    @Test
    @DisplayName("enroll: 404 si el periodo no existe")
    void enrollShouldFailWhenPeriodNotFound() {
        when(academicPeriodRepository.findById(999L)).thenReturn(Optional.empty());

        EnrollmentRequestDTO dto = request(201L);
        dto.setAcademicPeriodId(999L);

        assertThatThrownBy(() -> service.enroll(30L, dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Academic period not found with id: 999");
    }

    // ---------- enroll: seleccion de secciones ----------

    @Test
    @DisplayName("enroll: rechaza la misma seccion dos veces en la peticion")
    void enrollShouldRejectRepeatedSectionInRequest() {
        assertThatThrownBy(() -> service.enroll(30L, request(201L, 201L, 202L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("The same section cannot be enrolled twice");

        verify(enrollmentRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("enroll: 404 si la seccion no existe")
    void enrollShouldFailWhenSectionNotFound() {
        when(sectionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enroll(30L, request(999L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Section not found with id: 999");
    }

    @Test
    @DisplayName("enroll: la seccion debe pertenecer al periodo inscrito")
    void enrollShouldRejectSectionOfAnotherPeriod() {
        Section otherPeriodSection = section(
                205L, tap103, closedPeriod, 30, blocks(ScheduleDay.V, 5, ScheduleDay.V, 6, ScheduleDay.J, 6));
        when(sectionRepository.findById(205L)).thenReturn(Optional.of(otherPeriodSection));

        assertThatThrownBy(() -> service.enroll(30L, request(205L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("does not belong to academic period '2026-1'");

        verify(enrollmentRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("enroll: solo asignaturas del plan del estudiante")
    void enrollShouldRejectSubjectOutsideStudyPlan() {
        Subject outside = subject(105L, "OTR101", "Asignatura de otro plan", 1, otherPlan);
        Section outsideSection = section(
                206L, outside, openPeriod, 30, blocks(ScheduleDay.V, 2, ScheduleDay.V, 3, ScheduleDay.V, 4));
        when(sectionRepository.findById(206L)).thenReturn(Optional.of(outsideSection));

        assertThatThrownBy(() -> service.enroll(30L, request(206L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("does not belong to the student's study plan");

        verify(enrollmentRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("enroll: una asignatura aprobada no se vuelve a inscribir")
    void enrollShouldRejectApprovedSubject() {
        when(academicHistoryRepository.findByStudentIdAndResult(30L, AcademicHistoryResult.APROBADO))
                .thenReturn(List.of(approved("TAP101", "Programacion I")));

        assertThatThrownBy(() -> service.enroll(30L, request(201L, 202L, 203L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("'TAP101' is already APROBADO and cannot be enrolled again");

        verify(enrollmentRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("enroll: la misma asignatura no se inscribe dos veces en el periodo")
    void enrollShouldRejectSubjectAlreadyEnrolledInPeriod() {
        when(enrollmentRepository.findByStudentIdAndAcademicPeriodIdAndStatus(
                30L, 10L, EnrollmentStatus.INSCRITA)).thenReturn(List.of(enrollmentOf(1L, sec101)));

        assertThatThrownBy(() -> service.enroll(30L, request(201L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("'TAP101' is already enrolled in academic period '2026-1'");

        verify(enrollmentRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("enroll: los prerrequisitos deben estar aprobados")
    void enrollShouldRejectSubjectWithPendingPrerequisite() {
        assertThatThrownBy(() -> service.enroll(30L, request(204L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("requires prerequisite 'TAP102', which is not APROBADO");

        verify(enrollmentRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("enroll: no inscribe un semestre superior con pendientes anteriores disponibles")
    void enrollShouldRejectHigherSemesterWhileLowerPending() {
        when(academicHistoryRepository.findByStudentIdAndResult(30L, AcademicHistoryResult.APROBADO))
                .thenReturn(List.of(approved("TAP102", "Matematica I")));

        assertThatThrownBy(() -> service.enroll(30L, request(201L, 204L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("'TAP201' (semester 2) cannot be enrolled while 'TAP103' (semester 1) is still available");

        verify(enrollmentRepository, never()).saveAll(anyList());
    }

    // ---------- enroll: cupos, horario y cantidad ----------

    @Test
    @DisplayName("enroll: la seccion debe tener cupos disponibles")
    void enrollShouldRejectFullSection() {
        when(enrollmentRepository.countBySectionIdAndStatus(202L, EnrollmentStatus.INSCRITA)).thenReturn(2L);

        assertThatThrownBy(() -> service.enroll(30L, request(201L, 202L, 203L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("'TAP102' has no available seats (capacity 2)");

        verify(enrollmentRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("enroll: rechaza el choque de horario entre las secciones seleccionadas")
    void enrollShouldRejectScheduleConflictBetweenSelected() {
        sec103.replaceScheduleBlocks(blocks(ScheduleDay.L, 1, ScheduleDay.W, 3, ScheduleDay.J, 1));

        assertThatThrownBy(() -> service.enroll(30L, request(201L, 202L, 203L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Schedule conflict on L module 1 between 'TAP101' and 'TAP103'");

        verify(enrollmentRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("enroll: rechaza el choque con una seccion ya inscrita")
    void enrollShouldRejectScheduleConflictWithCurrentEnrollment() {
        when(enrollmentRepository.findByStudentIdAndAcademicPeriodIdAndStatus(
                30L, 10L, EnrollmentStatus.INSCRITA)).thenReturn(List.of(enrollmentOf(1L, sec101)));
        sec103.replaceScheduleBlocks(blocks(ScheduleDay.M, 1, ScheduleDay.W, 3, ScheduleDay.J, 1));

        assertThatThrownBy(() -> service.enroll(30L, request(202L, 203L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Schedule conflict on M module 1 between 'TAP101' and 'TAP103'");

        verify(enrollmentRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("enroll: no se pasa de 6 asignaturas por periodo")
    void enrollShouldRejectMoreThanSixSubjects() {
        List<Enrollment> current = List.of(
                enrollmentOf(1L, section(301L, subject(301L, "EXT301", "Extra 1", 1, plan), openPeriod, 30,
                        blocks(ScheduleDay.V, 1))),
                enrollmentOf(2L, section(302L, subject(302L, "EXT302", "Extra 2", 1, plan), openPeriod, 30,
                        blocks(ScheduleDay.V, 2))),
                enrollmentOf(3L, section(303L, subject(303L, "EXT303", "Extra 3", 1, plan), openPeriod, 30,
                        blocks(ScheduleDay.V, 3))),
                enrollmentOf(4L, section(304L, subject(304L, "EXT304", "Extra 4", 1, plan), openPeriod, 30,
                        blocks(ScheduleDay.V, 4))));

        when(enrollmentRepository.findByStudentIdAndAcademicPeriodIdAndStatus(
                30L, 10L, EnrollmentStatus.INSCRITA)).thenReturn(current);

        assertThatThrownBy(() -> service.enroll(30L, request(201L, 202L, 203L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot enroll more than 6 subjects per academic period (7 given)");

        verify(enrollmentRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("enroll: exige al menos 3 asignaturas cuando hay disponibles")
    void enrollShouldRejectFewerThanThreeSubjects() {
        assertThatThrownBy(() -> service.enroll(30L, request(201L, 202L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("must enroll at least 3 subjects in this academic period (2 given)");

        verify(enrollmentRepository, never()).saveAll(anyList());
    }

    // ---------- findAvailable ----------

    @Test
    @DisplayName("findAvailable: lista las asignaturas inscribibles con sus cupos")
    void findAvailableShouldListCandidates() {
        when(enrollmentRepository.countBySectionIdAndStatus(202L, EnrollmentStatus.INSCRITA)).thenReturn(1L);

        List<AvailableSectionResponseDTO> result = service.findAvailable(30L, 10L);

        // TAP201 queda fuera: su prerrequisito TAP102 no esta aprobado
        assertThat(result).extracting(AvailableSectionResponseDTO::getSubjectCode)
                .containsExactly("TAP101", "TAP102", "TAP103");

        AvailableSectionResponseDTO matematica = result.stream()
                .filter(dto -> dto.getSubjectCode().equals("TAP102"))
                .findFirst()
                .orElseThrow();
        assertThat(matematica.getCapacity()).isEqualTo(2);
        assertThat(matematica.getEnrolled()).isEqualTo(1L);
        assertThat(matematica.getAvailableSeats()).isEqualTo(1L);
        assertThat(matematica.getScheduleBlocks()).hasSize(3);
    }

    @Test
    @DisplayName("findAvailable: deja fuera las aprobadas y suma las que ya cumplen prerrequisitos")
    void findAvailableShouldExcludeApprovedAndIncludeUnlocked() {
        when(academicHistoryRepository.findByStudentIdAndResult(30L, AcademicHistoryResult.APROBADO))
                .thenReturn(List.of(approved("TAP102", "Matematica I")));

        assertThat(service.findAvailable(30L, 10L))
                .extracting(AvailableSectionResponseDTO::getSubjectCode)
                .containsExactly("TAP101", "TAP103", "TAP201");
    }

    @Test
    @DisplayName("findAvailable: deja fuera lo que ya esta inscrito")
    void findAvailableShouldExcludeAlreadyEnrolled() {
        when(enrollmentRepository.findByStudentIdAndAcademicPeriodIdAndStatus(
                30L, 10L, EnrollmentStatus.INSCRITA)).thenReturn(List.of(enrollmentOf(1L, sec101)));

        assertThat(service.findAvailable(30L, 10L))
                .extracting(AvailableSectionResponseDTO::getSubjectCode)
                .containsExactly("TAP102", "TAP103");
    }

    @Test
    @DisplayName("findAvailable: 409 si el periodo esta CERRADO")
    void findAvailableShouldRejectClosedPeriod() {
        assertThatThrownBy(() -> service.findAvailable(30L, 11L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("is CERRADO: enrollments cannot be changed");
    }

    @Test
    @DisplayName("findAvailable: 404 si el estudiante no existe")
    void findAvailableShouldFailWhenStudentNotFound() {
        when(studentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findAvailable(999L, 10L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Student not found with id: 999");
    }

    @Test
    @DisplayName("findAvailable: 404 si el periodo no existe")
    void findAvailableShouldFailWhenPeriodNotFound() {
        when(academicPeriodRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findAvailable(30L, 999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Academic period not found with id: 999");
    }

    // ---------- findByStudent ----------

    @Test
    @DisplayName("findByStudent: devuelve las inscripciones del estudiante")
    void findByStudentShouldReturnEnrollments() {
        when(studentRepository.existsById(30L)).thenReturn(true);
        when(enrollmentRepository.findByStudentId(30L))
                .thenReturn(List.of(enrollmentOf(1L, sec101), enrollmentOf(2L, sec102)));

        List<EnrollmentResponseDTO> result = service.findByStudent(30L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getSubjectCode()).isEqualTo("TAP101");
        assertThat(result.get(0).getScheduleBlocks()).hasSize(3);
    }

    @Test
    @DisplayName("findByStudent: 404 si el estudiante no existe")
    void findByStudentShouldFailWhenStudentNotFound() {
        when(studentRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> service.findByStudent(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Student not found with id: 999");
    }

    // ---------- cancel ----------

    @Test
    @DisplayName("cancel: la inscripcion pasa a ANULADA y libera el cupo")
    void cancelShouldAnnulEnrollment() {
        Enrollment enrollment = enrollmentOf(1L, sec101);
        when(enrollmentRepository.findByIdAndStudentId(1L, 30L)).thenReturn(Optional.of(enrollment));
        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.cancel(30L, 1L);

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.ANULADA);
        verify(enrollmentRepository).save(enrollment);
    }

    @Test
    @DisplayName("cancel: una inscripcion ya anulada no se anula de nuevo")
    void cancelShouldRejectAlreadyCancelled() {
        Enrollment enrollment = enrollmentOf(1L, sec101);
        enrollment.setStatus(EnrollmentStatus.ANULADA);
        when(enrollmentRepository.findByIdAndStudentId(1L, 30L)).thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> service.cancel(30L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("is already ANULADA");

        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancel: no se anula con el periodo CERRADO")
    void cancelShouldRejectClosedPeriod() {
        Enrollment enrollment = enrollmentOf(1L, sec101);
        enrollment.setAcademicPeriod(closedPeriod);
        when(enrollmentRepository.findByIdAndStudentId(1L, 30L)).thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> service.cancel(30L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("is CERRADO: enrollments cannot be changed");

        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancel: una inscripcion ya calificada no se anula")
    void cancelShouldRejectGradedEnrollment() {
        Enrollment enrollment = enrollmentOf(1L, sec101);
        when(enrollmentRepository.findByIdAndStudentId(1L, 30L)).thenReturn(Optional.of(enrollment));
        when(gradeRepository.existsByEnrollmentId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.cancel(30L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("has a final grade recorded and cannot be cancelled");

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.INSCRITA);
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancel: 404 si la inscripcion no es del estudiante")
    void cancelShouldFailWhenEnrollmentNotFoundForStudent() {
        when(enrollmentRepository.findByIdAndStudentId(999L, 30L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(30L, 999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Enrollment not found with id: 999 for student 30");

        verify(enrollmentRepository, never()).save(any());
    }
}
