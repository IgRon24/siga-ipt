package com.example.siga.service.impl;

import com.example.siga.dto.StudentRequestDTO;
import com.example.siga.dto.StudentResponseDTO;
import com.example.siga.entity.Career;
import com.example.siga.entity.CareerStatus;
import com.example.siga.entity.Student;
import com.example.siga.entity.StudentStatus;
import com.example.siga.entity.StudyPlan;
import com.example.siga.entity.StudyPlanStatus;
import com.example.siga.exception.BusinessException;
import com.example.siga.exception.ResourceNotFoundException;
import com.example.siga.mapper.StudentMapper;
import com.example.siga.repository.AcademicHistoryRepository;
import com.example.siga.repository.CareerRepository;
import com.example.siga.repository.EnrollmentRepository;
import com.example.siga.repository.StudentPeriodStatusRepository;
import com.example.siga.repository.StudentRepository;
import com.example.siga.repository.StudyPlanRepository;
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
class StudentServiceImplTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private CareerRepository careerRepository;

    @Mock
    private StudyPlanRepository studyPlanRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private AcademicHistoryRepository academicHistoryRepository;

    @Mock
    private StudentPeriodStatusRepository studentPeriodStatusRepository;

    private StudentServiceImpl service;

    private Career career;
    private StudyPlan currentPlan;
    private Student existing;

    @BeforeEach
    void setUp() {
        service = new StudentServiceImpl(
                studentRepository, careerRepository, studyPlanRepository, enrollmentRepository,
                academicHistoryRepository, studentPeriodStatusRepository, new StudentMapper());

        career = Career.builder()
                .id(10L)
                .code("10450")
                .name("Tecnico Analista Programador")
                .description("Carrera de dos anios")
                .status(CareerStatus.ACTIVA)
                .durationSemesters(4)
                .build();

        currentPlan = StudyPlan.builder()
                .id(100L)
                .identifier("2021.3")
                .name("Plan 2021")
                .status(StudyPlanStatus.VIGENTE)
                .career(career)
                .build();

        existing = Student.builder()
                .id(1L)
                .run("12345678-9")
                .fullName("Pedro Soto")
                .email("pedro.soto@ipt.cl")
                .status(StudentStatus.REGULAR)
                .career(career)
                .studyPlan(currentPlan)
                .build();
    }

    private StudentRequestDTO request() {
        return StudentRequestDTO.builder()
                .run("12345678-9")
                .fullName("Pedro Soto")
                .email("pedro.soto@ipt.cl")
                .careerId(10L)
                .build();
    }

    // ---------- create ----------

    @Test
    @DisplayName("create: registra al estudiante como REGULAR y en el plan VIGENTE de la carrera")
    void createShouldForceRegularStatusAndCurrentPlan() {
        when(studentRepository.existsByRun("12345678-9")).thenReturn(false);
        when(studentRepository.existsByEmail("pedro.soto@ipt.cl")).thenReturn(false);
        when(careerRepository.findById(10L)).thenReturn(Optional.of(career));
        when(studyPlanRepository.findByCareerIdAndStatus(10L, StudyPlanStatus.VIGENTE))
                .thenReturn(Optional.of(currentPlan));
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> {
            Student saved = inv.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        StudentResponseDTO result = service.create(request());

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getRun()).isEqualTo("12345678-9");
        assertThat(result.getStatus()).isEqualTo(StudentStatus.REGULAR);
        assertThat(result.getCareerId()).isEqualTo(10L);
        assertThat(result.getCareerCode()).isEqualTo("10450");
        assertThat(result.getStudyPlanId()).isEqualTo(100L);
        assertThat(result.getStudyPlanIdentifier()).isEqualTo("2021.3");

        ArgumentCaptor<Student> captor = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(StudentStatus.REGULAR);
        assertThat(captor.getValue().getStudyPlan()).isEqualTo(currentPlan);
    }

    @Test
    @DisplayName("create: rechaza un RUN ya registrado")
    void createShouldRejectDuplicateRun() {
        when(studentRepository.existsByRun("12345678-9")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("RUN '12345678-9' already exists");

        verify(studentRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: rechaza un email ya registrado")
    void createShouldRejectDuplicateEmail() {
        when(studentRepository.existsByRun("12345678-9")).thenReturn(false);
        when(studentRepository.existsByEmail("pedro.soto@ipt.cl")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("email 'pedro.soto@ipt.cl' already exists");

        verify(studentRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: 404 si la carrera no existe")
    void createShouldFailWhenCareerNotFound() {
        when(studentRepository.existsByRun(anyString())).thenReturn(false);
        when(studentRepository.existsByEmail(anyString())).thenReturn(false);
        when(careerRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Career not found with id: 10");

        verify(studentRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: una carrera INACTIVA no recibe nuevos estudiantes")
    void createShouldRejectInactiveCareer() {
        career.setStatus(CareerStatus.INACTIVA);
        when(studentRepository.existsByRun(anyString())).thenReturn(false);
        when(studentRepository.existsByEmail(anyString())).thenReturn(false);
        when(careerRepository.findById(10L)).thenReturn(Optional.of(career));

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("is INACTIVA and cannot receive students");

        verify(studentRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: rechaza si la carrera no tiene plan VIGENTE")
    void createShouldRejectCareerWithoutCurrentPlan() {
        when(studentRepository.existsByRun(anyString())).thenReturn(false);
        when(studentRepository.existsByEmail(anyString())).thenReturn(false);
        when(careerRepository.findById(10L)).thenReturn(Optional.of(career));
        when(studyPlanRepository.findByCareerIdAndStatus(10L, StudyPlanStatus.VIGENTE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("has no VIGENTE study plan");

        verify(studentRepository, never()).save(any());
    }

    // ---------- lecturas ----------

    @Test
    @DisplayName("findById: devuelve al estudiante con su carrera y plan")
    void findByIdShouldReturnStudent() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(existing));

        StudentResponseDTO result = service.findById(1L);

        assertThat(result.getFullName()).isEqualTo("Pedro Soto");
        assertThat(result.getCareerName()).isEqualTo("Tecnico Analista Programador");
        assertThat(result.getStudyPlanIdentifier()).isEqualTo("2021.3");
    }

    @Test
    @DisplayName("findById: 404 si el estudiante no existe")
    void findByIdShouldFailWhenNotFound() {
        when(studentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Student not found with id: 999");
    }

    @Test
    @DisplayName("findAll: devuelve todos los estudiantes")
    void findAllShouldReturnStudents() {
        when(studentRepository.findAll()).thenReturn(List.of(existing));

        assertThat(service.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("findByStatus: filtra por estado, sin importar mayusculas")
    void findByStatusShouldFilter() {
        when(studentRepository.findByStatus(StudentStatus.REGULAR)).thenReturn(List.of(existing));

        List<StudentResponseDTO> result = service.findByStatus("regular");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(StudentStatus.REGULAR);
    }

    @Test
    @DisplayName("findByStatus: rechaza un estado nulo")
    void findByStatusShouldRejectNullStatus() {
        assertThatThrownBy(() -> service.findByStatus(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Status is required");
    }

    @Test
    @DisplayName("findByStatus: rechaza un estado invalido")
    void findByStatusShouldRejectInvalidStatus() {
        assertThatThrownBy(() -> service.findByStatus("CONGELADO"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid status: CONGELADO");
    }

    @Test
    @DisplayName("findByCareer: devuelve los estudiantes de la carrera")
    void findByCareerShouldFilter() {
        when(careerRepository.existsById(10L)).thenReturn(true);
        when(studentRepository.findByCareerId(10L)).thenReturn(List.of(existing));

        assertThat(service.findByCareer(10L)).hasSize(1);
    }

    @Test
    @DisplayName("findByCareer: 404 si la carrera no existe")
    void findByCareerShouldFailWhenCareerNotFound() {
        when(careerRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.findByCareer(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Career not found with id: 99");
    }

    @Test
    @DisplayName("findByEmail: devuelve al estudiante del token")
    void findByEmailShouldReturnStudent() {
        when(studentRepository.findByEmail("pedro.soto@ipt.cl")).thenReturn(Optional.of(existing));

        assertThat(service.findByEmail("pedro.soto@ipt.cl").getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("findByEmail: 404 si el email del token no corresponde a un estudiante")
    void findByEmailShouldFailWhenNotFound() {
        when(studentRepository.findByEmail("otro@ipt.cl")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByEmail("otro@ipt.cl"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Student not found with email: otro@ipt.cl");
    }

    // ---------- update ----------

    @Test
    @DisplayName("update: modifica los datos y no toca el estado ni el plan")
    void updateShouldNotChangeStatusNorPlan() {
        existing.setStatus(StudentStatus.POSTERGACION);
        when(studentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        StudentRequestDTO dto = request();
        dto.setFullName("Pedro Pablo Soto");

        StudentResponseDTO result = service.update(1L, dto);

        assertThat(result.getFullName()).isEqualTo("Pedro Pablo Soto");
        assertThat(result.getStatus()).isEqualTo(StudentStatus.POSTERGACION);
        assertThat(result.getStudyPlanId()).isEqualTo(100L);

        verify(careerRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("update: al cambiar de carrera reasigna el plan VIGENTE de la nueva carrera")
    void updateShouldReassignPlanWhenCareerChanges() {
        Career otherCareer = Career.builder()
                .id(20L)
                .code("10452")
                .name("Tecnico en Ciberseguridad")
                .description("Carrera de dos anios")
                .status(CareerStatus.ACTIVA)
                .durationSemesters(4)
                .build();
        StudyPlan otherPlan = StudyPlan.builder()
                .id(200L)
                .identifier("2022.3")
                .name("Plan 2022")
                .status(StudyPlanStatus.VIGENTE)
                .career(otherCareer)
                .build();

        when(studentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(enrollmentRepository.existsByStudentId(1L)).thenReturn(false);
        when(academicHistoryRepository.existsByStudentId(1L)).thenReturn(false);
        when(careerRepository.findById(20L)).thenReturn(Optional.of(otherCareer));
        when(studyPlanRepository.findByCareerIdAndStatus(20L, StudyPlanStatus.VIGENTE))
                .thenReturn(Optional.of(otherPlan));
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        StudentRequestDTO dto = request();
        dto.setCareerId(20L);

        StudentResponseDTO result = service.update(1L, dto);

        assertThat(result.getCareerId()).isEqualTo(20L);
        assertThat(result.getStudyPlanId()).isEqualTo(200L);
        assertThat(result.getStudyPlanIdentifier()).isEqualTo("2022.3");
    }

    @Test
    @DisplayName("update: no permite trasladar a una carrera INACTIVA")
    void updateShouldRejectInactiveCareer() {
        Career inactive = Career.builder()
                .id(20L)
                .code("10452")
                .name("Tecnico en Ciberseguridad")
                .description("Carrera de dos anios")
                .status(CareerStatus.INACTIVA)
                .durationSemesters(4)
                .build();

        when(studentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(enrollmentRepository.existsByStudentId(1L)).thenReturn(false);
        when(academicHistoryRepository.existsByStudentId(1L)).thenReturn(false);
        when(careerRepository.findById(20L)).thenReturn(Optional.of(inactive));

        StudentRequestDTO dto = request();
        dto.setCareerId(20L);

        assertThatThrownBy(() -> service.update(1L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("is INACTIVA and cannot receive students");

        verify(studentRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: permite guardar sin cambiar el RUN ni el email")
    void updateShouldAllowSameRunAndEmail() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.update(1L, request())).isNotNull();

        verify(studentRepository, never()).existsByRun(anyString());
        verify(studentRepository, never()).existsByEmail(anyString());
    }

    @Test
    @DisplayName("update: rechaza cambiar el RUN a uno ya registrado")
    void updateShouldRejectDuplicateRun() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(studentRepository.existsByRun("98765432-1")).thenReturn(true);

        StudentRequestDTO dto = request();
        dto.setRun("98765432-1");

        assertThatThrownBy(() -> service.update(1L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("RUN '98765432-1' already exists");

        verify(studentRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: rechaza cambiar el email a uno ya registrado")
    void updateShouldRejectDuplicateEmail() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(studentRepository.existsByEmail("otro@ipt.cl")).thenReturn(true);

        StudentRequestDTO dto = request();
        dto.setEmail("otro@ipt.cl");

        assertThatThrownBy(() -> service.update(1L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("email 'otro@ipt.cl' already exists");

        verify(studentRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: 404 si el estudiante no existe")
    void updateShouldFailWhenNotFound() {
        when(studentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(999L, request()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Student not found with id: 999");
    }

    // ---------- changeStatus ----------

    @Test
    @DisplayName("changeStatus: el admin puede dejar al estudiante en POSTERGACION")
    void changeStatusShouldAllowPostergacion() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        StudentResponseDTO result = service.changeStatus(1L, "POSTERGACION");

        assertThat(result.getStatus()).isEqualTo(StudentStatus.POSTERGACION);
    }

    @Test
    @DisplayName("changeStatus: desde RETIRO_TEMPORAL puede volver a REGULAR")
    void changeStatusShouldAllowBackToRegular() {
        existing.setStatus(StudentStatus.RETIRO_TEMPORAL);
        when(studentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        StudentResponseDTO result = service.changeStatus(1L, "regular");

        assertThat(result.getStatus()).isEqualTo(StudentStatus.REGULAR);
        assertThat(result.getRun()).isEqualTo("12345678-9");
    }

    @Test
    @DisplayName("changeStatus: EGRESADO no se asigna manualmente")
    void changeStatusShouldRejectEgresado() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.changeStatus(1L, "EGRESADO"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("assigned automatically during the academic period closure");

        verify(studentRepository, never()).save(any());
    }

    @Test
    @DisplayName("changeStatus: ELIMINADO no se asigna manualmente")
    void changeStatusShouldRejectEliminado() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.changeStatus(1L, "ELIMINADO"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("assigned automatically during the academic period closure");

        verify(studentRepository, never()).save(any());
    }

    @Test
    @DisplayName("changeStatus: un estudiante ELIMINADO no vuelve a REGULAR")
    void changeStatusShouldRejectReactivatingEliminado() {
        existing.setStatus(StudentStatus.ELIMINADO);
        when(studentRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.changeStatus(1L, "REGULAR"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("A student in status ELIMINADO cannot change status");

        verify(studentRepository, never()).save(any());
    }

    @Test
    @DisplayName("changeStatus: un estudiante EGRESADO no cambia de estado")
    void changeStatusShouldRejectChangingEgresado() {
        existing.setStatus(StudentStatus.EGRESADO);
        when(studentRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.changeStatus(1L, "POSTERGACION"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("A student in status EGRESADO cannot change status");

        verify(studentRepository, never()).save(any());
    }

    @Test
    @DisplayName("changeStatus: rechaza un estado invalido")
    void changeStatusShouldRejectInvalidStatus() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.changeStatus(1L, "SUSPENDIDO"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid status: SUSPENDIDO");

        verify(studentRepository, never()).save(any());
    }

    @Test
    @DisplayName("changeStatus: 404 si el estudiante no existe")
    void changeStatusShouldFailWhenNotFound() {
        when(studentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeStatus(999L, "POSTERGACION"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Student not found with id: 999");
    }

    // ---------- delete ----------

    @Test
    @DisplayName("delete: elimina al estudiante")
    void deleteShouldRemoveStudent() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(enrollmentRepository.existsByStudentId(1L)).thenReturn(false);
        when(academicHistoryRepository.existsByStudentId(1L)).thenReturn(false);

        service.delete(1L);

        verify(studentPeriodStatusRepository).deleteByStudentId(1L);
        verify(studentRepository).delete(existing);
    }

    @Test
    @DisplayName("delete: no elimina a un estudiante con inscripciones")
    void deleteShouldRejectStudentWithEnrollments() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(enrollmentRepository.existsByStudentId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("has enrollments and cannot be deleted");

        verify(studentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete: no elimina a un estudiante con historial academico")
    void deleteShouldRejectStudentWithAcademicHistory() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(enrollmentRepository.existsByStudentId(1L)).thenReturn(false);
        when(academicHistoryRepository.existsByStudentId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("has academic history and cannot be deleted");

        verify(studentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("update: no cambia de carrera a un estudiante con inscripciones")
    void updateShouldRejectCareerChangeWithEnrollments() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(enrollmentRepository.existsByStudentId(1L)).thenReturn(true);

        StudentRequestDTO dto = request();
        dto.setCareerId(20L);

        assertThatThrownBy(() -> service.update(1L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("has enrollments and cannot change career or study plan");

        verify(studentRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: no cambia de carrera a un estudiante con historial academico")
    void updateShouldRejectCareerChangeWithAcademicHistory() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(enrollmentRepository.existsByStudentId(1L)).thenReturn(false);
        when(academicHistoryRepository.existsByStudentId(1L)).thenReturn(true);

        StudentRequestDTO dto = request();
        dto.setCareerId(20L);

        assertThatThrownBy(() -> service.update(1L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("has academic history and cannot change career or study plan");

        verify(studentRepository, never()).save(any());
    }

    @Test
    @DisplayName("delete: 404 si el estudiante no existe")
    void deleteShouldFailWhenNotFound() {
        when(studentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Student not found with id: 999");

        verify(studentRepository, never()).delete(any());
    }
}
