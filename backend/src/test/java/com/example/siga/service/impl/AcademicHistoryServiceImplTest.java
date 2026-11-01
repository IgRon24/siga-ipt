package com.example.siga.service.impl;

import com.example.siga.dto.AcademicHistoryResponseDTO;
import com.example.siga.entity.AcademicHistory;
import com.example.siga.entity.AcademicHistoryResult;
import com.example.siga.entity.AcademicPeriod;
import com.example.siga.entity.AcademicPeriodStatus;
import com.example.siga.entity.Career;
import com.example.siga.entity.Student;
import com.example.siga.entity.StudentStatus;
import com.example.siga.entity.StudyPlan;
import com.example.siga.entity.StudyPlanStatus;
import com.example.siga.exception.ResourceNotFoundException;
import com.example.siga.mapper.AcademicHistoryMapper;
import com.example.siga.repository.AcademicHistoryRepository;
import com.example.siga.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AcademicHistoryServiceImplTest {

    @Mock
    private AcademicHistoryRepository academicHistoryRepository;

    @Mock
    private StudentRepository studentRepository;

    private AcademicHistoryServiceImpl service;

    private Student student;
    private AcademicPeriod first;
    private AcademicPeriod second;

    @BeforeEach
    void setUp() {
        service = new AcademicHistoryServiceImpl(
                academicHistoryRepository, studentRepository, new AcademicHistoryMapper());

        Career career = Career.builder().id(1L).code("10450").name("Tecnico Analista Programador").build();
        StudyPlan plan = StudyPlan.builder()
                .id(2L).identifier("2021.3").name("Plan 2021").career(career)
                .status(StudyPlanStatus.VIGENTE)
                .build();

        student = Student.builder()
                .id(30L).run("22222222-2").fullName("Juan Soto").email("juan.soto@ipt.cl")
                .status(StudentStatus.REGULAR).career(career).studyPlan(plan)
                .build();

        first = AcademicPeriod.builder()
                .id(10L).code("2025-1").year(2025).semester(1)
                .status(AcademicPeriodStatus.CERRADO)
                .build();

        second = AcademicPeriod.builder()
                .id(11L).code("2025-2").year(2025).semester(2)
                .status(AcademicPeriodStatus.CERRADO)
                .build();
    }

    private AcademicHistory record(Long id, AcademicPeriod period, String code, String value,
                                   AcademicHistoryResult result) {
        return AcademicHistory.builder()
                .id(id).student(student).subjectCode(code).subjectName("Asignatura " + code)
                .academicPeriod(period).grade(new BigDecimal(value)).result(result)
                .build();
    }

    @Test
    @DisplayName("findByStudent: devuelve el snapshot ordenado por periodo y asignatura")
    void findByStudentShouldReturnSortedHistory() {
        when(studentRepository.existsById(30L)).thenReturn(true);
        when(academicHistoryRepository.findByStudentId(30L)).thenReturn(List.of(
                record(3L, second, "TCS103", "6.0", AcademicHistoryResult.APROBADO),
                record(2L, first, "TCS102", "2.5", AcademicHistoryResult.REPROBADO),
                record(1L, first, "TCS101", "5.0", AcademicHistoryResult.APROBADO)
        ));

        List<AcademicHistoryResponseDTO> result = service.findByStudent(30L);

        assertThat(result).extracting(AcademicHistoryResponseDTO::getSubjectCode)
                .containsExactly("TCS101", "TCS102", "TCS103");
        assertThat(result).extracting(AcademicHistoryResponseDTO::getAcademicPeriodCode)
                .containsExactly("2025-1", "2025-1", "2025-2");
        assertThat(result.get(0).getStudentRun()).isEqualTo("22222222-2");
        assertThat(result.get(0).getSubjectName()).isEqualTo("Asignatura TCS101");
        assertThat(result.get(0).getGrade()).isEqualByComparingTo("5.0");
        assertThat(result.get(1).getResult()).isEqualTo(AcademicHistoryResult.REPROBADO);
    }

    @Test
    @DisplayName("findByStudent: un estudiante sin historial devuelve lista vacia")
    void findByStudentShouldReturnEmptyHistory() {
        when(studentRepository.existsById(30L)).thenReturn(true);
        when(academicHistoryRepository.findByStudentId(30L)).thenReturn(List.of());

        assertThat(service.findByStudent(30L)).isEmpty();
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
