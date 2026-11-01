package com.example.siga.service.impl;

import com.example.siga.dto.StudentPeriodStatusRequestDTO;
import com.example.siga.dto.StudentPeriodStatusResponseDTO;
import com.example.siga.entity.AcademicPeriod;
import com.example.siga.entity.AcademicPeriodStatus;
import com.example.siga.entity.Career;
import com.example.siga.entity.CareerStatus;
import com.example.siga.entity.MatriculationStatus;
import com.example.siga.entity.Student;
import com.example.siga.entity.StudentPeriodStatus;
import com.example.siga.entity.StudentStatus;
import com.example.siga.entity.StudyPlan;
import com.example.siga.entity.StudyPlanStatus;
import com.example.siga.exception.BusinessException;
import com.example.siga.exception.ResourceNotFoundException;
import com.example.siga.mapper.StudentPeriodStatusMapper;
import com.example.siga.repository.AcademicPeriodRepository;
import com.example.siga.repository.StudentPeriodStatusRepository;
import com.example.siga.repository.StudentRepository;
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
class StudentPeriodStatusServiceImplTest {

    @Mock
    private StudentPeriodStatusRepository studentPeriodStatusRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private AcademicPeriodRepository academicPeriodRepository;

    private StudentPeriodStatusServiceImpl service;

    private Student student;
    private AcademicPeriod openPeriod;
    private AcademicPeriod closedPeriod;

    @BeforeEach
    void setUp() {
        service = new StudentPeriodStatusServiceImpl(
                studentPeriodStatusRepository, studentRepository, academicPeriodRepository,
                new StudentPeriodStatusMapper());

        Career career = Career.builder()
                .id(1L).code("10450").name("Tecnico Analista Programador")
                .description("Carrera tecnica").status(CareerStatus.ACTIVA).durationSemesters(4)
                .build();

        StudyPlan plan = StudyPlan.builder()
                .id(50L).identifier("2021.3").name("Plan 2021")
                .status(StudyPlanStatus.VIGENTE).career(career)
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
    }

    private StudentPeriodStatusRequestDTO request(MatriculationStatus status) {
        return StudentPeriodStatusRequestDTO.builder()
                .studentId(30L)
                .academicPeriodId(10L)
                .enrollmentStatus(status)
                .build();
    }

    // ---------- save ----------

    @Test
    @DisplayName("save: registra la matricula del estudiante en el periodo")
    void saveShouldCreateMatriculation() {
        when(studentRepository.findById(30L)).thenReturn(Optional.of(student));
        when(academicPeriodRepository.findById(10L)).thenReturn(Optional.of(openPeriod));
        when(studentPeriodStatusRepository.findByStudentIdAndAcademicPeriodId(30L, 10L))
                .thenReturn(Optional.empty());
        when(studentPeriodStatusRepository.save(any(StudentPeriodStatus.class))).thenAnswer(inv -> {
            StudentPeriodStatus saved = inv.getArgument(0);
            saved.setId(7L);
            return saved;
        });

        StudentPeriodStatusResponseDTO result = service.save(request(MatriculationStatus.MATRICULATED));

        assertThat(result.getId()).isEqualTo(7L);
        assertThat(result.getStudentRun()).isEqualTo("12345678-9");
        assertThat(result.getAcademicPeriodCode()).isEqualTo("2026-1");
        assertThat(result.getEnrollmentStatus()).isEqualTo(MatriculationStatus.MATRICULATED);
    }

    @Test
    @DisplayName("save: actualiza la matricula existente en lugar de duplicarla")
    void saveShouldUpdateExistingMatriculation() {
        StudentPeriodStatus existing = StudentPeriodStatus.builder()
                .id(7L).student(student).academicPeriod(openPeriod)
                .enrollmentStatus(MatriculationStatus.MATRICULATED)
                .build();

        when(studentRepository.findById(30L)).thenReturn(Optional.of(student));
        when(academicPeriodRepository.findById(10L)).thenReturn(Optional.of(openPeriod));
        when(studentPeriodStatusRepository.findByStudentIdAndAcademicPeriodId(30L, 10L))
                .thenReturn(Optional.of(existing));
        when(studentPeriodStatusRepository.save(any(StudentPeriodStatus.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        StudentPeriodStatusResponseDTO result = service.save(request(MatriculationStatus.NOT_MATRICULATED));

        assertThat(result.getId()).isEqualTo(7L);
        assertThat(result.getEnrollmentStatus()).isEqualTo(MatriculationStatus.NOT_MATRICULATED);

        ArgumentCaptor<StudentPeriodStatus> captor = ArgumentCaptor.forClass(StudentPeriodStatus.class);
        verify(studentPeriodStatusRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("save: no se toca la matricula de un periodo CERRADO")
    void saveShouldRejectClosedPeriod() {
        when(studentRepository.findById(30L)).thenReturn(Optional.of(student));
        when(academicPeriodRepository.findById(11L)).thenReturn(Optional.of(closedPeriod));

        StudentPeriodStatusRequestDTO dto = request(MatriculationStatus.MATRICULATED);
        dto.setAcademicPeriodId(11L);

        assertThatThrownBy(() -> service.save(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("is CERRADO and its matriculation cannot be changed");

        verify(studentPeriodStatusRepository, never()).save(any());
    }

    @Test
    @DisplayName("save: 404 si el estudiante no existe")
    void saveShouldFailWhenStudentNotFound() {
        when(studentRepository.findById(30L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(request(MatriculationStatus.MATRICULATED)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Student not found with id: 30");

        verify(studentPeriodStatusRepository, never()).save(any());
    }

    @Test
    @DisplayName("save: 404 si el periodo no existe")
    void saveShouldFailWhenPeriodNotFound() {
        when(studentRepository.findById(30L)).thenReturn(Optional.of(student));
        when(academicPeriodRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(request(MatriculationStatus.MATRICULATED)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Academic period not found with id: 10");

        verify(studentPeriodStatusRepository, never()).save(any());
    }

    // ---------- lecturas ----------

    @Test
    @DisplayName("findByStudentAndPeriod: devuelve la matricula")
    void findByStudentAndPeriodShouldReturnRecord() {
        StudentPeriodStatus existing = StudentPeriodStatus.builder()
                .id(7L).student(student).academicPeriod(openPeriod)
                .enrollmentStatus(MatriculationStatus.MATRICULATED)
                .build();

        when(studentPeriodStatusRepository.findByStudentIdAndAcademicPeriodId(30L, 10L))
                .thenReturn(Optional.of(existing));

        assertThat(service.findByStudentAndPeriod(30L, 10L).getEnrollmentStatus())
                .isEqualTo(MatriculationStatus.MATRICULATED);
    }

    @Test
    @DisplayName("findByStudentAndPeriod: 404 si no hay registro de matricula")
    void findByStudentAndPeriodShouldFailWhenMissing() {
        when(studentPeriodStatusRepository.findByStudentIdAndAcademicPeriodId(30L, 10L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByStudentAndPeriod(30L, 10L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("has no matriculation record for academic period 10");
    }

    @Test
    @DisplayName("findByPeriod: devuelve las matriculas del periodo")
    void findByPeriodShouldReturnRecords() {
        StudentPeriodStatus existing = StudentPeriodStatus.builder()
                .id(7L).student(student).academicPeriod(openPeriod)
                .enrollmentStatus(MatriculationStatus.MATRICULATED)
                .build();

        when(academicPeriodRepository.existsById(10L)).thenReturn(true);
        when(studentPeriodStatusRepository.findByAcademicPeriodId(10L)).thenReturn(List.of(existing));

        assertThat(service.findByPeriod(10L)).hasSize(1);
    }

    @Test
    @DisplayName("findByPeriod: 404 si el periodo no existe")
    void findByPeriodShouldFailWhenPeriodNotFound() {
        when(academicPeriodRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> service.findByPeriod(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Academic period not found with id: 999");
    }

    @Test
    @DisplayName("findByStudent: devuelve las matriculas del estudiante")
    void findByStudentShouldReturnRecords() {
        StudentPeriodStatus existing = StudentPeriodStatus.builder()
                .id(7L).student(student).academicPeriod(openPeriod)
                .enrollmentStatus(MatriculationStatus.MATRICULATED)
                .build();

        when(studentRepository.existsById(30L)).thenReturn(true);
        when(studentPeriodStatusRepository.findByStudentId(30L)).thenReturn(List.of(existing));

        assertThat(service.findByStudent(30L)).hasSize(1);
    }

    @Test
    @DisplayName("findByStudent: 404 si el estudiante no existe")
    void findByStudentShouldFailWhenStudentNotFound() {
        when(studentRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> service.findByStudent(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Student not found with id: 999");
    }
}
