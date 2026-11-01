package com.example.siga.service.impl;

import com.example.siga.dto.ScheduleBlockRequestDTO;
import com.example.siga.dto.SectionRequestDTO;
import com.example.siga.dto.SectionResponseDTO;
import com.example.siga.entity.AcademicPeriod;
import com.example.siga.entity.AcademicPeriodStatus;
import com.example.siga.entity.Career;
import com.example.siga.entity.CareerStatus;
import com.example.siga.entity.EnrollmentStatus;
import com.example.siga.entity.ScheduleBlock;
import com.example.siga.entity.ScheduleDay;
import com.example.siga.entity.Section;
import com.example.siga.entity.StudyPlan;
import com.example.siga.entity.StudyPlanStatus;
import com.example.siga.entity.Subject;
import com.example.siga.entity.Teacher;
import com.example.siga.entity.TeacherStatus;
import com.example.siga.exception.BusinessException;
import com.example.siga.exception.ResourceNotFoundException;
import com.example.siga.mapper.SectionMapper;
import com.example.siga.repository.AcademicPeriodRepository;
import com.example.siga.repository.EnrollmentRepository;
import com.example.siga.repository.SectionRepository;
import com.example.siga.repository.SubjectRepository;
import com.example.siga.repository.TeacherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SectionServiceImplTest {

    @Mock
    private SectionRepository sectionRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private AcademicPeriodRepository academicPeriodRepository;

    @Mock
    private TeacherRepository teacherRepository;

    private SectionServiceImpl service;

    private Subject subject;          // TEL 2+2+2 = 6 horas -> 3 bloques
    private AcademicPeriod openPeriod;
    private AcademicPeriod closedPeriod;
    private Teacher activeTeacher;
    private Section existing;

    @BeforeEach
    void setUp() {
        service = new SectionServiceImpl(
                sectionRepository, enrollmentRepository, subjectRepository, academicPeriodRepository,
                teacherRepository, new SectionMapper());

        Career career = Career.builder()
                .id(1L).code("10450").name("Tecnico Analista Programador")
                .description("Carrera tecnica").status(CareerStatus.ACTIVA).durationSemesters(4)
                .build();

        StudyPlan plan = StudyPlan.builder()
                .id(50L).identifier("2021.3").name("Plan 2021")
                .status(StudyPlanStatus.VIGENTE).career(career)
                .build();

        subject = Subject.builder()
                .id(100L).code("TAP101").name("Programacion I").semester(1)
                .telTheory(2).telExercises(2).telLab(2).sct(5).studyPlan(plan)
                .build();

        openPeriod = AcademicPeriod.builder()
                .id(10L).code("2026-1").year(2026).semester(1)
                .status(AcademicPeriodStatus.ABIERTO)
                .build();

        closedPeriod = AcademicPeriod.builder()
                .id(11L).code("2025-2").year(2025).semester(2)
                .status(AcademicPeriodStatus.CERRADO)
                .build();

        activeTeacher = Teacher.builder()
                .id(20L).run("12345678-9").fullName("Ana Perez").email("ana.perez@ipt.cl")
                .professionalTitle("Ingeniera en Informatica").academicDegree("Magister")
                .status(TeacherStatus.ACTIVO)
                .build();

        existing = Section.builder()
                .id(5L)
                .subject(subject)
                .academicPeriod(openPeriod)
                .teacher(activeTeacher)
                .capacity(30)
                .scheduleBlocks(new ArrayList<>())
                .build();
        existing.replaceScheduleBlocks(blocks(ScheduleDay.L, 1, ScheduleDay.L, 2, ScheduleDay.M, 1));
    }

    /** Arma bloques a partir de pares dia/modulo. */
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

    private List<ScheduleBlockRequestDTO> blockDtos(Object... dayModulePairs) {
        List<ScheduleBlockRequestDTO> result = new ArrayList<>();
        for (int i = 0; i < dayModulePairs.length; i += 2) {
            result.add(ScheduleBlockRequestDTO.builder()
                    .day((ScheduleDay) dayModulePairs[i])
                    .module((Integer) dayModulePairs[i + 1])
                    .build());
        }
        return result;
    }

    private SectionRequestDTO request() {
        return SectionRequestDTO.builder()
                .subjectId(100L)
                .academicPeriodId(10L)
                .teacherId(20L)
                .capacity(30)
                .scheduleBlocks(blockDtos(ScheduleDay.L, 1, ScheduleDay.L, 2, ScheduleDay.M, 1))
                .build();
    }

    private Section otherSectionWith(List<ScheduleBlock> sectionBlocks) {
        Section other = Section.builder()
                .id(9L)
                .subject(subject)
                .academicPeriod(openPeriod)
                .teacher(activeTeacher)
                .capacity(20)
                .scheduleBlocks(new ArrayList<>())
                .build();
        other.replaceScheduleBlocks(sectionBlocks);
        return other;
    }

    // ---------- create ----------

    @Test
    @DisplayName("create: crea la seccion con su horario completo")
    void createShouldSaveSectionWithSchedule() {
        when(subjectRepository.findById(100L)).thenReturn(Optional.of(subject));
        when(academicPeriodRepository.findById(10L)).thenReturn(Optional.of(openPeriod));
        when(sectionRepository.existsBySubjectIdAndAcademicPeriodId(100L, 10L)).thenReturn(false);
        when(teacherRepository.findById(20L)).thenReturn(Optional.of(activeTeacher));
        when(sectionRepository.findByTeacherIdAndAcademicPeriodId(20L, 10L)).thenReturn(List.of());
        when(sectionRepository.save(any(Section.class))).thenAnswer(inv -> {
            Section saved = inv.getArgument(0);
            saved.setId(5L);
            return saved;
        });

        SectionResponseDTO result = service.create(request());

        assertThat(result.getId()).isEqualTo(5L);
        assertThat(result.getSubjectCode()).isEqualTo("TAP101");
        assertThat(result.getAcademicPeriodCode()).isEqualTo("2026-1");
        assertThat(result.getTeacherFullName()).isEqualTo("Ana Perez");
        assertThat(result.getCapacity()).isEqualTo(30);
        assertThat(result.getScheduleBlocks()).hasSize(3);
    }

    @Test
    @DisplayName("create: el docente puede tener otra seccion en dias y modulos distintos")
    void createShouldAllowTeacherWithoutOverlap() {
        when(subjectRepository.findById(100L)).thenReturn(Optional.of(subject));
        when(academicPeriodRepository.findById(10L)).thenReturn(Optional.of(openPeriod));
        when(sectionRepository.existsBySubjectIdAndAcademicPeriodId(100L, 10L)).thenReturn(false);
        when(teacherRepository.findById(20L)).thenReturn(Optional.of(activeTeacher));
        when(sectionRepository.findByTeacherIdAndAcademicPeriodId(20L, 10L))
                .thenReturn(List.of(otherSectionWith(blocks(ScheduleDay.W, 3, ScheduleDay.J, 4, ScheduleDay.V, 5))));
        when(sectionRepository.save(any(Section.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.create(request()).getScheduleBlocks()).hasSize(3);
    }

    @Test
    @DisplayName("create: 404 si la asignatura no existe")
    void createShouldFailWhenSubjectNotFound() {
        when(subjectRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Subject not found with id: 100");

        verify(sectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: 404 si el periodo academico no existe")
    void createShouldFailWhenPeriodNotFound() {
        when(subjectRepository.findById(100L)).thenReturn(Optional.of(subject));
        when(academicPeriodRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Academic period not found with id: 10");

        verify(sectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: no se arma oferta sobre un periodo CERRADO")
    void createShouldRejectClosedPeriod() {
        when(subjectRepository.findById(100L)).thenReturn(Optional.of(subject));
        when(academicPeriodRepository.findById(11L)).thenReturn(Optional.of(closedPeriod));

        SectionRequestDTO dto = request();
        dto.setAcademicPeriodId(11L);

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("is CERRADO and its sections cannot be modified");

        verify(sectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: una asignatura tiene una unica seccion por periodo")
    void createShouldRejectDuplicateSection() {
        when(subjectRepository.findById(100L)).thenReturn(Optional.of(subject));
        when(academicPeriodRepository.findById(10L)).thenReturn(Optional.of(openPeriod));
        when(sectionRepository.existsBySubjectIdAndAcademicPeriodId(100L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already has a section in academic period '2026-1'");

        verify(sectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: 404 si el docente no existe")
    void createShouldFailWhenTeacherNotFound() {
        when(subjectRepository.findById(100L)).thenReturn(Optional.of(subject));
        when(academicPeriodRepository.findById(10L)).thenReturn(Optional.of(openPeriod));
        when(sectionRepository.existsBySubjectIdAndAcademicPeriodId(100L, 10L)).thenReturn(false);
        when(teacherRepository.findById(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Teacher not found with id: 20");

        verify(sectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: el docente responsable debe estar ACTIVO")
    void createShouldRejectInactiveTeacher() {
        activeTeacher.setStatus(TeacherStatus.INACTIVO);
        when(subjectRepository.findById(100L)).thenReturn(Optional.of(subject));
        when(academicPeriodRepository.findById(10L)).thenReturn(Optional.of(openPeriod));
        when(sectionRepository.existsBySubjectIdAndAcademicPeriodId(100L, 10L)).thenReturn(false);
        when(teacherRepository.findById(20L)).thenReturn(Optional.of(activeTeacher));

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("is INACTIVO and cannot be assigned to a section");

        verify(sectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: la cantidad de bloques debe ser la suma TEL dividida en 2")
    void createShouldRejectWrongBlockCount() {
        when(subjectRepository.findById(100L)).thenReturn(Optional.of(subject));
        when(academicPeriodRepository.findById(10L)).thenReturn(Optional.of(openPeriod));
        when(sectionRepository.existsBySubjectIdAndAcademicPeriodId(100L, 10L)).thenReturn(false);
        when(teacherRepository.findById(20L)).thenReturn(Optional.of(activeTeacher));

        SectionRequestDTO dto = request();
        dto.setScheduleBlocks(blockDtos(ScheduleDay.L, 1, ScheduleDay.L, 2));

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("requires 3 and 2 were given");

        verify(sectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: no admite bloques repetidos")
    void createShouldRejectDuplicatedBlocks() {
        when(subjectRepository.findById(100L)).thenReturn(Optional.of(subject));
        when(academicPeriodRepository.findById(10L)).thenReturn(Optional.of(openPeriod));
        when(sectionRepository.existsBySubjectIdAndAcademicPeriodId(100L, 10L)).thenReturn(false);
        when(teacherRepository.findById(20L)).thenReturn(Optional.of(activeTeacher));

        SectionRequestDTO dto = request();
        dto.setScheduleBlocks(blockDtos(ScheduleDay.L, 1, ScheduleDay.L, 1, ScheduleDay.M, 1));

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Duplicated schedule block: L module 1");

        verify(sectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: rechaza el choque de horario del docente")
    void createShouldRejectTeacherScheduleConflict() {
        when(subjectRepository.findById(100L)).thenReturn(Optional.of(subject));
        when(academicPeriodRepository.findById(10L)).thenReturn(Optional.of(openPeriod));
        when(sectionRepository.existsBySubjectIdAndAcademicPeriodId(100L, 10L)).thenReturn(false);
        when(teacherRepository.findById(20L)).thenReturn(Optional.of(activeTeacher));
        when(sectionRepository.findByTeacherIdAndAcademicPeriodId(20L, 10L))
                .thenReturn(List.of(otherSectionWith(blocks(ScheduleDay.M, 1, ScheduleDay.W, 3, ScheduleDay.J, 4))));

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already has a section on M module 1");

        verify(sectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: una asignatura sin horas TEL no puede tener bloques")
    void createShouldRejectSubjectWithoutTelHours() {
        subject.setTelTheory(0);
        subject.setTelExercises(0);
        subject.setTelLab(0);
        when(subjectRepository.findById(100L)).thenReturn(Optional.of(subject));
        when(academicPeriodRepository.findById(10L)).thenReturn(Optional.of(openPeriod));
        when(sectionRepository.existsBySubjectIdAndAcademicPeriodId(100L, 10L)).thenReturn(false);
        when(teacherRepository.findById(20L)).thenReturn(Optional.of(activeTeacher));

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("has no TEL hours");

        verify(sectionRepository, never()).save(any());
    }

    // ---------- lecturas ----------

    @Test
    @DisplayName("findById: devuelve la seccion con su horario")
    void findByIdShouldReturnSection() {
        when(sectionRepository.findById(5L)).thenReturn(Optional.of(existing));

        SectionResponseDTO result = service.findById(5L);

        assertThat(result.getSubjectCode()).isEqualTo("TAP101");
        assertThat(result.getScheduleBlocks()).hasSize(3);
    }

    @Test
    @DisplayName("findById: 404 si la seccion no existe")
    void findByIdShouldFailWhenNotFound() {
        when(sectionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Section not found with id: 999");
    }

    @Test
    @DisplayName("findAll: devuelve todas las secciones")
    void findAllShouldReturnSections() {
        when(sectionRepository.findAll()).thenReturn(List.of(existing));

        assertThat(service.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("findByAcademicPeriod: devuelve la oferta del periodo")
    void findByAcademicPeriodShouldFilter() {
        when(academicPeriodRepository.existsById(10L)).thenReturn(true);
        when(sectionRepository.findByAcademicPeriodId(10L)).thenReturn(List.of(existing));

        assertThat(service.findByAcademicPeriod(10L)).hasSize(1);
    }

    @Test
    @DisplayName("findByAcademicPeriod: 404 si el periodo no existe")
    void findByAcademicPeriodShouldFailWhenPeriodNotFound() {
        when(academicPeriodRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> service.findByAcademicPeriod(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Academic period not found with id: 999");
    }

    @Test
    @DisplayName("findByTeacher: devuelve las secciones del docente")
    void findByTeacherShouldFilter() {
        when(teacherRepository.existsById(20L)).thenReturn(true);
        when(sectionRepository.findByTeacherId(20L)).thenReturn(List.of(existing));

        assertThat(service.findByTeacher(20L)).hasSize(1);
    }

    @Test
    @DisplayName("findByTeacher: 404 si el docente no existe")
    void findByTeacherShouldFailWhenTeacherNotFound() {
        when(teacherRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> service.findByTeacher(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Teacher not found with id: 999");
    }

    // ---------- update ----------

    @Test
    @DisplayName("update: cambia la capacidad y el horario de la seccion")
    void updateShouldModifySection() {
        when(sectionRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(subjectRepository.findById(100L)).thenReturn(Optional.of(subject));
        when(academicPeriodRepository.findById(10L)).thenReturn(Optional.of(openPeriod));
        when(teacherRepository.findById(20L)).thenReturn(Optional.of(activeTeacher));
        when(sectionRepository.findByTeacherIdAndAcademicPeriodId(20L, 10L)).thenReturn(List.of(existing));
        when(sectionRepository.save(any(Section.class))).thenAnswer(inv -> inv.getArgument(0));

        SectionRequestDTO dto = request();
        dto.setCapacity(45);
        dto.setScheduleBlocks(blockDtos(ScheduleDay.W, 1, ScheduleDay.W, 2, ScheduleDay.J, 3));

        SectionResponseDTO result = service.update(5L, dto);

        assertThat(result.getCapacity()).isEqualTo(45);
        assertThat(result.getScheduleBlocks()).hasSize(3);
        assertThat(result.getScheduleBlocks().get(0).getDay()).isEqualTo(ScheduleDay.W);
    }

    @Test
    @DisplayName("update: el horario propio de la seccion no cuenta como choque")
    void updateShouldIgnoreItsOwnBlocksWhenCheckingConflicts() {
        when(sectionRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(subjectRepository.findById(100L)).thenReturn(Optional.of(subject));
        when(academicPeriodRepository.findById(10L)).thenReturn(Optional.of(openPeriod));
        when(teacherRepository.findById(20L)).thenReturn(Optional.of(activeTeacher));
        when(sectionRepository.findByTeacherIdAndAcademicPeriodId(20L, 10L)).thenReturn(List.of(existing));
        when(sectionRepository.save(any(Section.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.update(5L, request()).getScheduleBlocks()).hasSize(3);
    }

    @Test
    @DisplayName("update: mantener un docente que quedo INACTIVO no rompe la seccion")
    void updateShouldAllowKeepingInactiveTeacher() {
        activeTeacher.setStatus(TeacherStatus.INACTIVO);
        when(sectionRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(subjectRepository.findById(100L)).thenReturn(Optional.of(subject));
        when(academicPeriodRepository.findById(10L)).thenReturn(Optional.of(openPeriod));
        when(teacherRepository.findById(20L)).thenReturn(Optional.of(activeTeacher));
        when(sectionRepository.findByTeacherIdAndAcademicPeriodId(20L, 10L)).thenReturn(List.of(existing));
        when(sectionRepository.save(any(Section.class))).thenAnswer(inv -> inv.getArgument(0));

        SectionRequestDTO dto = request();
        dto.setCapacity(50);

        assertThat(service.update(5L, dto).getCapacity()).isEqualTo(50);
    }

    @Test
    @DisplayName("update: reasigna la seccion a otro docente ACTIVO")
    void updateShouldAllowAssigningAnotherActiveTeacher() {
        Teacher other = Teacher.builder()
                .id(21L).run("98765432-1").fullName("Luis Soto").email("luis.soto@ipt.cl")
                .professionalTitle("Ingeniero").academicDegree("Magister")
                .status(TeacherStatus.ACTIVO)
                .build();

        when(sectionRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(subjectRepository.findById(100L)).thenReturn(Optional.of(subject));
        when(academicPeriodRepository.findById(10L)).thenReturn(Optional.of(openPeriod));
        when(teacherRepository.findById(21L)).thenReturn(Optional.of(other));
        when(sectionRepository.findByTeacherIdAndAcademicPeriodId(21L, 10L)).thenReturn(List.of());
        when(sectionRepository.save(any(Section.class))).thenAnswer(inv -> inv.getArgument(0));

        SectionRequestDTO dto = request();
        dto.setTeacherId(21L);

        SectionResponseDTO result = service.update(5L, dto);

        assertThat(result.getTeacherId()).isEqualTo(21L);
        assertThat(result.getTeacherFullName()).isEqualTo("Luis Soto");
    }

    @Test
    @DisplayName("update: no se puede asignar un docente INACTIVO")
    void updateShouldRejectAssigningInactiveTeacher() {
        Teacher inactive = Teacher.builder()
                .id(21L).run("98765432-1").fullName("Luis Soto").email("luis.soto@ipt.cl")
                .professionalTitle("Ingeniero").academicDegree("Magister")
                .status(TeacherStatus.INACTIVO)
                .build();

        when(sectionRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(subjectRepository.findById(100L)).thenReturn(Optional.of(subject));
        when(academicPeriodRepository.findById(10L)).thenReturn(Optional.of(openPeriod));
        when(teacherRepository.findById(21L)).thenReturn(Optional.of(inactive));

        SectionRequestDTO dto = request();
        dto.setTeacherId(21L);

        assertThatThrownBy(() -> service.update(5L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("is INACTIVO and cannot be assigned to a section");

        verify(sectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: no se modifica una seccion de un periodo CERRADO")
    void updateShouldRejectSectionOfClosedPeriod() {
        existing.setAcademicPeriod(closedPeriod);
        when(sectionRepository.findById(5L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(5L, request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("is CERRADO and its sections cannot be modified");

        verify(sectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: no se puede mover la seccion a un periodo CERRADO")
    void updateShouldRejectMovingToClosedPeriod() {
        when(sectionRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(subjectRepository.findById(100L)).thenReturn(Optional.of(subject));
        when(academicPeriodRepository.findById(11L)).thenReturn(Optional.of(closedPeriod));

        SectionRequestDTO dto = request();
        dto.setAcademicPeriodId(11L);

        assertThatThrownBy(() -> service.update(5L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("'2025-2' is CERRADO");

        verify(sectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: rechaza dejar dos secciones de la misma asignatura en el periodo")
    void updateShouldRejectDuplicateOffer() {
        Subject other = Subject.builder()
                .id(101L).code("TAP102").name("Matematica I").semester(1)
                .telTheory(2).telExercises(2).telLab(2).sct(5).studyPlan(subject.getStudyPlan())
                .build();

        when(sectionRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(subjectRepository.findById(101L)).thenReturn(Optional.of(other));
        when(academicPeriodRepository.findById(10L)).thenReturn(Optional.of(openPeriod));
        when(sectionRepository.existsBySubjectIdAndAcademicPeriodId(101L, 10L)).thenReturn(true);

        SectionRequestDTO dto = request();
        dto.setSubjectId(101L);

        assertThatThrownBy(() -> service.update(5L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already has a section in academic period '2026-1'");

        verify(sectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: 404 si la seccion no existe")
    void updateShouldFailWhenNotFound() {
        when(sectionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(999L, request()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Section not found with id: 999");
    }

    // ---------- replaceSchedule ----------

    @Test
    @DisplayName("replaceSchedule: reemplaza el horario completo")
    void replaceScheduleShouldReplaceBlocks() {
        when(sectionRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(sectionRepository.findByTeacherIdAndAcademicPeriodId(20L, 10L)).thenReturn(List.of(existing));
        when(sectionRepository.save(any(Section.class))).thenAnswer(inv -> inv.getArgument(0));

        SectionResponseDTO result = service.replaceSchedule(
                5L, blockDtos(ScheduleDay.V, 1, ScheduleDay.V, 2, ScheduleDay.V, 3));

        assertThat(result.getScheduleBlocks()).hasSize(3);
        assertThat(result.getScheduleBlocks())
                .allSatisfy(block -> assertThat(block.getDay()).isEqualTo(ScheduleDay.V));
    }

    @Test
    @DisplayName("replaceSchedule: un bloque que se repite conserva su fila en vez de reinsertarse")
    void replaceScheduleShouldKeepRepeatedBlocks() {
        // Se simulan los ids que traeria la base para distinguir filas viejas de nuevas
        long nextId = 1L;
        for (ScheduleBlock block : existing.getScheduleBlocks()) {
            block.setId(nextId++);
        }
        ScheduleBlock keptL1 = existing.getScheduleBlocks().get(0);

        when(sectionRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(sectionRepository.findByTeacherIdAndAcademicPeriodId(20L, 10L)).thenReturn(List.of(existing));
        when(sectionRepository.save(any(Section.class))).thenAnswer(inv -> inv.getArgument(0));

        // El horario pasa de L1, L2, M1 a L1, L2, V3: los dos primeros se mantienen
        service.replaceSchedule(5L, blockDtos(ScheduleDay.L, 1, ScheduleDay.L, 2, ScheduleDay.V, 3));

        assertThat(existing.getScheduleBlocks()).hasSize(3);
        assertThat(existing.getScheduleBlocks()).noneMatch(block -> block.getDay() == ScheduleDay.M);

        // Reinsertar un dia+modulo que ya existe choca con el unique (section_id, day, module)
        assertThat(existing.getScheduleBlocks()).contains(keptL1);
        assertThat(existing.getScheduleBlocks())
                .filteredOn(block -> block.getId() == null)
                .singleElement()
                .satisfies(block -> {
                    assertThat(block.getDay()).isEqualTo(ScheduleDay.V);
                    assertThat(block.getModule()).isEqualTo(3);
                });
    }

    @Test
    @DisplayName("replaceSchedule: rechaza una cantidad de bloques que no cuadra con el TEL")
    void replaceScheduleShouldRejectWrongBlockCount() {
        when(sectionRepository.findById(5L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.replaceSchedule(5L, blockDtos(ScheduleDay.V, 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("requires 3 and 1 were given");

        verify(sectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("replaceSchedule: rechaza un horario vacio")
    void replaceScheduleShouldRejectEmptySchedule() {
        when(sectionRepository.findById(5L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.replaceSchedule(5L, List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("At least one schedule block is required");

        verify(sectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("replaceSchedule: rechaza un horario nulo")
    void replaceScheduleShouldRejectNullSchedule() {
        when(sectionRepository.findById(5L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.replaceSchedule(5L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("At least one schedule block is required");

        verify(sectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("replaceSchedule: rechaza bloques repetidos")
    void replaceScheduleShouldRejectDuplicatedBlocks() {
        when(sectionRepository.findById(5L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.replaceSchedule(
                5L, blockDtos(ScheduleDay.V, 2, ScheduleDay.V, 2, ScheduleDay.V, 3)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Duplicated schedule block: V module 2");

        verify(sectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("replaceSchedule: rechaza un bloque sin dia")
    void replaceScheduleShouldRejectBlockWithoutDay() {
        when(sectionRepository.findById(5L)).thenReturn(Optional.of(existing));

        List<ScheduleBlockRequestDTO> dtos = blockDtos(ScheduleDay.V, 1, ScheduleDay.V, 2, ScheduleDay.V, 3);
        dtos.get(0).setDay(null);

        assertThatThrownBy(() -> service.replaceSchedule(5L, dtos))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Day is required");

        verify(sectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("replaceSchedule: rechaza un modulo fuera de 1 a 6")
    void replaceScheduleShouldRejectModuleOutOfRange() {
        when(sectionRepository.findById(5L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.replaceSchedule(
                5L, blockDtos(ScheduleDay.V, 9, ScheduleDay.V, 2, ScheduleDay.V, 3)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Module must be between 1 and 6");

        verify(sectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("replaceSchedule: rechaza el choque con otra seccion del docente")
    void replaceScheduleShouldRejectTeacherConflict() {
        when(sectionRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(sectionRepository.findByTeacherIdAndAcademicPeriodId(20L, 10L))
                .thenReturn(Arrays.asList(
                        existing,
                        otherSectionWith(blocks(ScheduleDay.V, 1, ScheduleDay.W, 3, ScheduleDay.J, 4))));

        assertThatThrownBy(() -> service.replaceSchedule(
                5L, blockDtos(ScheduleDay.V, 1, ScheduleDay.V, 2, ScheduleDay.V, 3)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already has a section on V module 1");

        verify(sectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("replaceSchedule: no se toca el horario de un periodo CERRADO")
    void replaceScheduleShouldRejectClosedPeriod() {
        existing.setAcademicPeriod(closedPeriod);
        when(sectionRepository.findById(5L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.replaceSchedule(
                5L, blockDtos(ScheduleDay.V, 1, ScheduleDay.V, 2, ScheduleDay.V, 3)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("is CERRADO and its sections cannot be modified");

        verify(sectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("replaceSchedule: 404 si la seccion no existe")
    void replaceScheduleShouldFailWhenNotFound() {
        when(sectionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.replaceSchedule(999L, blockDtos(ScheduleDay.V, 1)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Section not found with id: 999");
    }

    // ---------- delete ----------

    @Test
    @DisplayName("delete: elimina la seccion de un periodo ABIERTO")
    void deleteShouldRemoveSection() {
        when(sectionRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(enrollmentRepository.existsBySectionIdAndStatus(5L, EnrollmentStatus.INSCRITA)).thenReturn(false);
        when(enrollmentRepository.existsBySectionId(5L)).thenReturn(false);

        service.delete(5L);

        verify(sectionRepository).delete(existing);
    }

    @Test
    @DisplayName("delete: no elimina una seccion con estudiantes inscritos")
    void deleteShouldRejectSectionWithEnrollments() {
        when(sectionRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(enrollmentRepository.existsBySectionIdAndStatus(5L, EnrollmentStatus.INSCRITA)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(5L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("has enrolled students and cannot be deleted");

        verify(sectionRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete: no elimina una seccion con inscripciones anuladas")
    void deleteShouldRejectSectionWithCancelledEnrollments() {
        when(sectionRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(enrollmentRepository.existsBySectionIdAndStatus(5L, EnrollmentStatus.INSCRITA)).thenReturn(false);
        when(enrollmentRepository.existsBySectionId(5L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(5L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("has ANULADA enrollment records and cannot be deleted");

        verify(sectionRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete: no elimina una seccion de un periodo CERRADO")
    void deleteShouldRejectClosedPeriod() {
        existing.setAcademicPeriod(closedPeriod);
        when(sectionRepository.findById(5L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.delete(5L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("is CERRADO and its sections cannot be modified");

        verify(sectionRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete: 404 si la seccion no existe")
    void deleteShouldFailWhenNotFound() {
        when(sectionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Section not found with id: 999");

        verify(sectionRepository, never()).delete(any());
    }
}
