package com.example.siga.service.impl;

import com.example.siga.dto.TeacherRequestDTO;
import com.example.siga.dto.TeacherResponseDTO;
import com.example.siga.entity.Teacher;
import com.example.siga.entity.TeacherStatus;
import com.example.siga.exception.BusinessException;
import com.example.siga.exception.ResourceNotFoundException;
import com.example.siga.entity.AcademicPeriodStatus;
import com.example.siga.mapper.TeacherMapper;
import com.example.siga.repository.SectionRepository;
import com.example.siga.repository.TeacherRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeacherServiceImplTest {

    @Mock
    private TeacherRepository teacherRepository;

    @Mock
    private SectionRepository sectionRepository;

    private TeacherServiceImpl service;

    private Teacher existing;

    @BeforeEach
    void setUp() {
        service = new TeacherServiceImpl(teacherRepository, sectionRepository, new TeacherMapper());

        existing = Teacher.builder()
                .id(1L)
                .run("12345678-9")
                .fullName("Ana Perez")
                .email("ana.perez@ipt.cl")
                .professionalTitle("Ingeniera en Informatica")
                .academicDegree("Magister en Ciencias de la Computacion")
                .status(TeacherStatus.ACTIVO)
                .build();
    }

    private TeacherRequestDTO request() {
        return TeacherRequestDTO.builder()
                .run("12345678-9")
                .fullName("Ana Perez")
                .email("ana.perez@ipt.cl")
                .professionalTitle("Ingeniera en Informatica")
                .academicDegree("Magister en Ciencias de la Computacion")
                .build();
    }

    // ---------- create ----------

    @Test
    @DisplayName("create: registra el docente y lo deja en estado ACTIVO")
    void createShouldForceActiveStatus() {
        when(teacherRepository.existsByRun("12345678-9")).thenReturn(false);
        when(teacherRepository.existsByEmail("ana.perez@ipt.cl")).thenReturn(false);
        when(teacherRepository.save(any(Teacher.class))).thenAnswer(inv -> {
            Teacher saved = inv.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        TeacherResponseDTO result = service.create(request());

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getRun()).isEqualTo("12345678-9");
        assertThat(result.getFullName()).isEqualTo("Ana Perez");
        assertThat(result.getStatus()).isEqualTo(TeacherStatus.ACTIVO);

        ArgumentCaptor<Teacher> captor = ArgumentCaptor.forClass(Teacher.class);
        verify(teacherRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TeacherStatus.ACTIVO);
    }

    @Test
    @DisplayName("create: rechaza un RUN ya registrado")
    void createShouldRejectDuplicateRun() {
        when(teacherRepository.existsByRun("12345678-9")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("RUN '12345678-9' already exists");

        verify(teacherRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: rechaza un email ya registrado")
    void createShouldRejectDuplicateEmail() {
        when(teacherRepository.existsByRun("12345678-9")).thenReturn(false);
        when(teacherRepository.existsByEmail("ana.perez@ipt.cl")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("email 'ana.perez@ipt.cl' already exists");

        verify(teacherRepository, never()).save(any());
    }

    // ---------- findById / findAll / findByStatus ----------

    @Test
    @DisplayName("findById: devuelve el docente")
    void findByIdShouldReturnTeacher() {
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(existing));

        TeacherResponseDTO result = service.findById(1L);

        assertThat(result.getEmail()).isEqualTo("ana.perez@ipt.cl");
        assertThat(result.getProfessionalTitle()).isEqualTo("Ingeniera en Informatica");
        assertThat(result.getAcademicDegree()).isEqualTo("Magister en Ciencias de la Computacion");
    }

    @Test
    @DisplayName("findById: 404 si el docente no existe")
    void findByIdShouldFailWhenNotFound() {
        when(teacherRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Teacher not found with id: 999");
    }

    @Test
    @DisplayName("findAll: devuelve todos los docentes")
    void findAllShouldReturnTeachers() {
        when(teacherRepository.findAll()).thenReturn(List.of(existing));

        assertThat(service.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("findByStatus: filtra por estado, sin importar mayusculas")
    void findByStatusShouldFilter() {
        when(teacherRepository.findByStatus(TeacherStatus.ACTIVO)).thenReturn(List.of(existing));

        List<TeacherResponseDTO> result = service.findByStatus("activo");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(TeacherStatus.ACTIVO);
    }

    @Test
    @DisplayName("findByEmail: identifica al docente del token")
    void findByEmailShouldReturnTeacher() {
        when(teacherRepository.findByEmail("ana.perez@ipt.cl")).thenReturn(Optional.of(existing));

        TeacherResponseDTO result = service.findByEmail("ana.perez@ipt.cl");

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getRun()).isEqualTo("12345678-9");
    }

    @Test
    @DisplayName("findByEmail: 404 si el email no corresponde a un docente")
    void findByEmailShouldFailWhenNotFound() {
        when(teacherRepository.findByEmail("nadie@ipt.cl")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByEmail("nadie@ipt.cl"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Teacher not found with email: nadie@ipt.cl");
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
        assertThatThrownBy(() -> service.findByStatus("JUBILADO"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid status: JUBILADO");
    }

    // ---------- update ----------

    @Test
    @DisplayName("update: modifica los datos y no toca el estado")
    void updateShouldNotChangeStatus() {
        existing.setStatus(TeacherStatus.INACTIVO);
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(teacherRepository.save(any(Teacher.class))).thenAnswer(inv -> inv.getArgument(0));

        TeacherRequestDTO dto = request();
        dto.setFullName("Ana Maria Perez");
        dto.setAcademicDegree("Doctora en Ciencias de la Computacion");

        TeacherResponseDTO result = service.update(1L, dto);

        assertThat(result.getFullName()).isEqualTo("Ana Maria Perez");
        assertThat(result.getAcademicDegree()).isEqualTo("Doctora en Ciencias de la Computacion");
        assertThat(result.getStatus()).isEqualTo(TeacherStatus.INACTIVO);
    }

    @Test
    @DisplayName("update: permite guardar sin cambiar el RUN ni el email")
    void updateShouldAllowSameRunAndEmail() {
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(teacherRepository.save(any(Teacher.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.update(1L, request())).isNotNull();

        verify(teacherRepository, never()).existsByRun(anyString());
        verify(teacherRepository, never()).existsByEmail(anyString());
    }

    @Test
    @DisplayName("update: rechaza cambiar el RUN a uno ya registrado")
    void updateShouldRejectDuplicateRun() {
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(teacherRepository.existsByRun("98765432-1")).thenReturn(true);

        TeacherRequestDTO dto = request();
        dto.setRun("98765432-1");

        assertThatThrownBy(() -> service.update(1L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("RUN '98765432-1' already exists");

        verify(teacherRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: rechaza cambiar el email a uno ya registrado")
    void updateShouldRejectDuplicateEmail() {
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(teacherRepository.existsByEmail("otro@ipt.cl")).thenReturn(true);

        TeacherRequestDTO dto = request();
        dto.setEmail("otro@ipt.cl");

        assertThatThrownBy(() -> service.update(1L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("email 'otro@ipt.cl' already exists");

        verify(teacherRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: 404 si el docente no existe")
    void updateShouldFailWhenNotFound() {
        when(teacherRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(999L, request()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Teacher not found with id: 999");
    }

    // ---------- changeStatus ----------

    @Test
    @DisplayName("changeStatus: pasa de ACTIVO a INACTIVO")
    void changeStatusShouldDeactivate() {
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(sectionRepository.existsByTeacherIdAndAcademicPeriodStatus(1L, AcademicPeriodStatus.ABIERTO))
                .thenReturn(false);
        when(teacherRepository.save(any(Teacher.class))).thenAnswer(inv -> inv.getArgument(0));

        TeacherResponseDTO result = service.changeStatus(1L, "INACTIVO");

        assertThat(result.getStatus()).isEqualTo(TeacherStatus.INACTIVO);
    }

    @Test
    @DisplayName("changeStatus: no inactiva a un docente con secciones en un periodo ABIERTO")
    void changeStatusShouldRejectDeactivationWithOpenSections() {
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(sectionRepository.existsByTeacherIdAndAcademicPeriodStatus(1L, AcademicPeriodStatus.ABIERTO))
                .thenReturn(true);

        assertThatThrownBy(() -> service.changeStatus(1L, "INACTIVO"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("has sections in an ABIERTO academic period");

        verify(teacherRepository, never()).save(any());
    }

    @Test
    @DisplayName("changeStatus: vuelve a ACTIVO sin alterar el resto de los datos")
    void changeStatusShouldReactivate() {
        existing.setStatus(TeacherStatus.INACTIVO);
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(teacherRepository.save(any(Teacher.class))).thenAnswer(inv -> inv.getArgument(0));

        TeacherResponseDTO result = service.changeStatus(1L, "activo");

        assertThat(result.getStatus()).isEqualTo(TeacherStatus.ACTIVO);
        assertThat(result.getRun()).isEqualTo("12345678-9");
        assertThat(result.getFullName()).isEqualTo("Ana Perez");
    }

    @Test
    @DisplayName("changeStatus: rechaza un estado invalido")
    void changeStatusShouldRejectInvalidStatus() {
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.changeStatus(1L, "SUSPENDIDO"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid status: SUSPENDIDO");

        verify(teacherRepository, never()).save(any());
    }

    @Test
    @DisplayName("changeStatus: 404 si el docente no existe")
    void changeStatusShouldFailWhenNotFound() {
        when(teacherRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeStatus(999L, "INACTIVO"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Teacher not found with id: 999");
    }

    // ---------- delete ----------

    @Test
    @DisplayName("delete: elimina el docente")
    void deleteShouldRemoveTeacher() {
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(sectionRepository.existsByTeacherId(1L)).thenReturn(false);

        service.delete(1L);

        verify(teacherRepository).delete(existing);
    }

    @Test
    @DisplayName("delete: no elimina a un docente con secciones asociadas")
    void deleteShouldRejectTeacherWithSections() {
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(sectionRepository.existsByTeacherId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("has sections associated and cannot be deleted");

        verify(teacherRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete: 404 si el docente no existe")
    void deleteShouldFailWhenNotFound() {
        when(teacherRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Teacher not found with id: 999");

        verify(teacherRepository, never()).delete(any());
    }
}
