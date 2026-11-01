package com.example.siga.service.impl;

import com.example.siga.dto.AcademicPeriodRequestDTO;
import com.example.siga.dto.AcademicPeriodResponseDTO;
import com.example.siga.entity.AcademicPeriod;
import com.example.siga.entity.AcademicPeriodStatus;
import com.example.siga.exception.BusinessException;
import com.example.siga.exception.ResourceNotFoundException;
import com.example.siga.mapper.AcademicPeriodMapper;
import com.example.siga.repository.AcademicPeriodRepository;
import com.example.siga.repository.SectionRepository;
import com.example.siga.repository.StudentPeriodStatusRepository;
import com.example.siga.service.AcademicClosureService;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AcademicPeriodServiceImplTest {

    @Mock
    private AcademicPeriodRepository academicPeriodRepository;

    @Mock
    private SectionRepository sectionRepository;

    @Mock
    private StudentPeriodStatusRepository studentPeriodStatusRepository;

    @Mock
    private AcademicClosureService academicClosureService;

    private AcademicPeriodServiceImpl service;

    private AcademicPeriod open;
    private AcademicPeriod closed;

    @BeforeEach
    void setUp() {
        service = new AcademicPeriodServiceImpl(
                academicPeriodRepository, sectionRepository, studentPeriodStatusRepository,
                academicClosureService, new AcademicPeriodMapper());

        open = AcademicPeriod.builder()
                .id(1L)
                .code("2026-1")
                .year(2026)
                .semester(1)
                .status(AcademicPeriodStatus.ABIERTO)
                .build();

        closed = AcademicPeriod.builder()
                .id(2L)
                .code("2025-2")
                .year(2025)
                .semester(2)
                .status(AcademicPeriodStatus.CERRADO)
                .build();
    }

    private AcademicPeriodRequestDTO request() {
        return AcademicPeriodRequestDTO.builder()
                .year(2026)
                .semester(1)
                .build();
    }

    // ---------- create ----------

    @Test
    @DisplayName("create: arma el codigo anio-semestre y deja el periodo ABIERTO")
    void createShouldBuildCodeAndOpenStatus() {
        when(academicPeriodRepository.existsByYearAndSemester(2026, 1)).thenReturn(false);
        when(academicPeriodRepository.save(any(AcademicPeriod.class))).thenAnswer(inv -> {
            AcademicPeriod saved = inv.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        AcademicPeriodResponseDTO result = service.create(request());

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getCode()).isEqualTo("2026-1");
        assertThat(result.getYear()).isEqualTo(2026);
        assertThat(result.getSemester()).isEqualTo(1);
        assertThat(result.getStatus()).isEqualTo(AcademicPeriodStatus.ABIERTO);

        ArgumentCaptor<AcademicPeriod> captor = ArgumentCaptor.forClass(AcademicPeriod.class);
        verify(academicPeriodRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(AcademicPeriodStatus.ABIERTO);
    }

    @Test
    @DisplayName("create: rechaza un anio y semestre ya registrados")
    void createShouldRejectDuplicatePeriod() {
        when(academicPeriodRepository.existsByYearAndSemester(2026, 1)).thenReturn(true);

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("An academic period '2026-1' already exists");

        verify(academicPeriodRepository, never()).save(any());
    }

    // ---------- lecturas ----------

    @Test
    @DisplayName("findById: devuelve el periodo")
    void findByIdShouldReturnPeriod() {
        when(academicPeriodRepository.findById(1L)).thenReturn(Optional.of(open));

        assertThat(service.findById(1L).getCode()).isEqualTo("2026-1");
    }

    @Test
    @DisplayName("findById: 404 si el periodo no existe")
    void findByIdShouldFailWhenNotFound() {
        when(academicPeriodRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Academic period not found with id: 999");
    }

    @Test
    @DisplayName("findAll: devuelve todos los periodos")
    void findAllShouldReturnPeriods() {
        when(academicPeriodRepository.findAll()).thenReturn(List.of(open, closed));

        assertThat(service.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("findByStatus: filtra por estado, sin importar mayusculas")
    void findByStatusShouldFilter() {
        when(academicPeriodRepository.findByStatus(AcademicPeriodStatus.ABIERTO)).thenReturn(List.of(open));

        List<AcademicPeriodResponseDTO> result = service.findByStatus("abierto");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(AcademicPeriodStatus.ABIERTO);
    }

    @Test
    @DisplayName("findByStatus: rechaza un estado invalido")
    void findByStatusShouldRejectInvalidStatus() {
        assertThatThrownBy(() -> service.findByStatus("EN_CURSO"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid status: EN_CURSO");
    }

    @Test
    @DisplayName("findByStatus: rechaza un estado nulo")
    void findByStatusShouldRejectNullStatus() {
        assertThatThrownBy(() -> service.findByStatus(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Status is required");
    }

    // ---------- update ----------

    @Test
    @DisplayName("update: cambia el anio y semestre y rearma el codigo")
    void updateShouldRebuildCode() {
        when(academicPeriodRepository.findById(1L)).thenReturn(Optional.of(open));
        when(academicPeriodRepository.existsByYearAndSemester(2027, 2)).thenReturn(false);
        when(academicPeriodRepository.save(any(AcademicPeriod.class))).thenAnswer(inv -> inv.getArgument(0));

        AcademicPeriodRequestDTO dto = AcademicPeriodRequestDTO.builder().year(2027).semester(2).build();

        AcademicPeriodResponseDTO result = service.update(1L, dto);

        assertThat(result.getCode()).isEqualTo("2027-2");
        assertThat(result.getStatus()).isEqualTo(AcademicPeriodStatus.ABIERTO);
    }

    @Test
    @DisplayName("update: permite guardar el mismo anio y semestre")
    void updateShouldAllowSameYearAndSemester() {
        when(academicPeriodRepository.findById(1L)).thenReturn(Optional.of(open));
        when(academicPeriodRepository.save(any(AcademicPeriod.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.update(1L, request())).isNotNull();

        verify(academicPeriodRepository, never()).existsByYearAndSemester(anyInt(), anyInt());
    }

    @Test
    @DisplayName("update: rechaza un anio y semestre ya usados por otro periodo")
    void updateShouldRejectDuplicatePeriod() {
        when(academicPeriodRepository.findById(1L)).thenReturn(Optional.of(open));
        when(academicPeriodRepository.existsByYearAndSemester(2025, 2)).thenReturn(true);

        AcademicPeriodRequestDTO dto = AcademicPeriodRequestDTO.builder().year(2025).semester(2).build();

        assertThatThrownBy(() -> service.update(1L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("An academic period '2025-2' already exists");

        verify(academicPeriodRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: un periodo CERRADO ya no se modifica")
    void updateShouldRejectClosedPeriod() {
        when(academicPeriodRepository.findById(2L)).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> service.update(2L, request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("is CERRADO and cannot be modified");

        verify(academicPeriodRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: 404 si el periodo no existe")
    void updateShouldFailWhenNotFound() {
        when(academicPeriodRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(999L, request()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Academic period not found with id: 999");
    }

    // ---------- changeStatus ----------

    @Test
    @DisplayName("changeStatus: cerrar delega en el cierre de periodo, que consolida el historial")
    void changeStatusShouldDelegateClosure() {
        when(academicPeriodRepository.findById(1L)).thenReturn(Optional.of(open));
        when(academicClosureService.close(1L)).thenAnswer(inv -> {
            open.setStatus(AcademicPeriodStatus.CERRADO);
            return null;
        });

        AcademicPeriodResponseDTO result = service.changeStatus(1L, "CERRADO");

        assertThat(result.getStatus()).isEqualTo(AcademicPeriodStatus.CERRADO);
        assertThat(result.getCode()).isEqualTo("2026-1");

        verify(academicClosureService).close(1L);
        // El estado lo guarda el cierre, no este servicio
        verify(academicPeriodRepository, never()).save(any());
    }

    @Test
    @DisplayName("changeStatus: ABIERTO sobre un periodo ABIERTO no pasa por el cierre")
    void changeStatusShouldNotDelegateWhenNotClosing() {
        when(academicPeriodRepository.findById(1L)).thenReturn(Optional.of(open));
        when(academicPeriodRepository.save(any(AcademicPeriod.class))).thenAnswer(inv -> inv.getArgument(0));

        AcademicPeriodResponseDTO result = service.changeStatus(1L, "ABIERTO");

        assertThat(result.getStatus()).isEqualTo(AcademicPeriodStatus.ABIERTO);
        verify(academicClosureService, never()).close(any());
    }

    @Test
    @DisplayName("changeStatus: un periodo CERRADO no se reabre")
    void changeStatusShouldRejectReopening() {
        when(academicPeriodRepository.findById(2L)).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> service.changeStatus(2L, "ABIERTO"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("is already CERRADO and cannot be reopened");

        verify(academicPeriodRepository, never()).save(any());
    }

    @Test
    @DisplayName("changeStatus: un periodo CERRADO tampoco se vuelve a cerrar")
    void changeStatusShouldRejectClosingTwice() {
        when(academicPeriodRepository.findById(2L)).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> service.changeStatus(2L, "CERRADO"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("is already CERRADO");

        verify(academicPeriodRepository, never()).save(any());
    }

    @Test
    @DisplayName("changeStatus: rechaza un estado invalido")
    void changeStatusShouldRejectInvalidStatus() {
        when(academicPeriodRepository.findById(1L)).thenReturn(Optional.of(open));

        assertThatThrownBy(() -> service.changeStatus(1L, "SUSPENDIDO"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid status: SUSPENDIDO");

        verify(academicPeriodRepository, never()).save(any());
    }

    @Test
    @DisplayName("changeStatus: 404 si el periodo no existe")
    void changeStatusShouldFailWhenNotFound() {
        when(academicPeriodRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeStatus(999L, "CERRADO"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Academic period not found with id: 999");
    }

    // ---------- delete ----------

    @Test
    @DisplayName("delete: elimina un periodo ABIERTO sin secciones")
    void deleteShouldRemovePeriod() {
        when(academicPeriodRepository.findById(1L)).thenReturn(Optional.of(open));
        when(sectionRepository.existsByAcademicPeriodId(1L)).thenReturn(false);
        when(studentPeriodStatusRepository.existsByAcademicPeriodId(1L)).thenReturn(false);

        service.delete(1L);

        verify(academicPeriodRepository).delete(open);
    }

    @Test
    @DisplayName("delete: no elimina un periodo con secciones")
    void deleteShouldRejectPeriodWithSections() {
        when(academicPeriodRepository.findById(1L)).thenReturn(Optional.of(open));
        when(sectionRepository.existsByAcademicPeriodId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("has sections associated and cannot be deleted");

        verify(academicPeriodRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete: no elimina un periodo con matriculas registradas")
    void deleteShouldRejectPeriodWithMatriculations() {
        when(academicPeriodRepository.findById(1L)).thenReturn(Optional.of(open));
        when(sectionRepository.existsByAcademicPeriodId(1L)).thenReturn(false);
        when(studentPeriodStatusRepository.existsByAcademicPeriodId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("has matriculation records and cannot be deleted");

        verify(academicPeriodRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete: no elimina un periodo CERRADO")
    void deleteShouldRejectClosedPeriod() {
        when(academicPeriodRepository.findById(2L)).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> service.delete(2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("is CERRADO and cannot be modified");

        verify(academicPeriodRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete: 404 si el periodo no existe")
    void deleteShouldFailWhenNotFound() {
        when(academicPeriodRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Academic period not found with id: 999");

        verify(academicPeriodRepository, never()).delete(any());
    }
}
