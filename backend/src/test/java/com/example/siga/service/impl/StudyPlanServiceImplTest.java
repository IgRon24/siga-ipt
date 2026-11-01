package com.example.siga.service.impl;

import com.example.siga.dto.StudyPlanRequestDTO;
import com.example.siga.dto.StudyPlanResponseDTO;
import com.example.siga.entity.Career;
import com.example.siga.entity.CareerStatus;
import com.example.siga.entity.StudyPlan;
import com.example.siga.entity.StudyPlanStatus;
import com.example.siga.exception.BusinessException;
import com.example.siga.exception.ResourceNotFoundException;
import com.example.siga.mapper.StudyPlanMapper;
import com.example.siga.repository.CareerRepository;
import com.example.siga.repository.StudentRepository;
import com.example.siga.repository.StudyPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class StudyPlanServiceImplTest {

    @Mock
    private StudyPlanRepository studyPlanRepository;

    @Mock
    private CareerRepository careerRepository;

    @Mock
    private StudentRepository studentRepository;

    private StudyPlanServiceImpl service;

    private Career career;
    private StudyPlan existing;
    private StudyPlan currentVigente;

    @BeforeEach
    void setUp() {
        service = new StudyPlanServiceImpl(
                studyPlanRepository, careerRepository, studentRepository, new StudyPlanMapper());

        career = Career.builder()
                .id(10L)
                .code("10450")
                .name("Tecnico Analista Programador")
                .description("Carrera tecnica de cuatro semestres")
                .status(CareerStatus.ACTIVA)
                .durationSemesters(4)
                .build();

        existing = StudyPlan.builder()
                .id(1L)
                .identifier("2022.3")
                .name("Plan 2022")
                .status(StudyPlanStatus.NO_VIGENTE)
                .career(career)
                .build();

        currentVigente = StudyPlan.builder()
                .id(2L)
                .identifier("2021.3")
                .name("Plan 2021")
                .status(StudyPlanStatus.VIGENTE)
                .career(career)
                .build();
    }

    private StudyPlanRequestDTO request(StudyPlanStatus status) {
        return StudyPlanRequestDTO.builder()
                .identifier("2022.3")
                .name("Plan 2022")
                .status(status)
                .careerId(10L)
                .build();
    }

    // ---------- create ----------

    @Test
    @DisplayName("create: registra el plan en la carrera indicada")
    void createShouldSavePlan() {
        when(careerRepository.findById(10L)).thenReturn(Optional.of(career));
        when(studyPlanRepository.existsByCareerIdAndIdentifier(10L, "2022.3")).thenReturn(false);
        when(studyPlanRepository.save(any(StudyPlan.class))).thenAnswer(inv -> {
            StudyPlan saved = inv.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        StudyPlanResponseDTO result = service.create(request(StudyPlanStatus.NO_VIGENTE));

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getIdentifier()).isEqualTo("2022.3");
        assertThat(result.getStatus()).isEqualTo(StudyPlanStatus.NO_VIGENTE);
        assertThat(result.getCareerId()).isEqualTo(10L);
        assertThat(result.getCareerName()).isEqualTo("Tecnico Analista Programador");
    }

    @Test
    @DisplayName("create: un plan NO_VIGENTE no toca al plan vigente de la carrera")
    void createNonCurrentPlanShouldNotDeactivateAnything() {
        when(careerRepository.findById(10L)).thenReturn(Optional.of(career));
        when(studyPlanRepository.existsByCareerIdAndIdentifier(10L, "2022.3")).thenReturn(false);
        when(studyPlanRepository.save(any(StudyPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(request(StudyPlanStatus.NO_VIGENTE));

        verify(studyPlanRepository, never()).findByCareerIdAndStatus(anyLong(), any());
    }

    @Test
    @DisplayName("create: un plan VIGENTE deja NO_VIGENTE al plan vigente anterior")
    void createCurrentPlanShouldDeactivatePrevious() {
        when(careerRepository.findById(10L)).thenReturn(Optional.of(career));
        when(studyPlanRepository.existsByCareerIdAndIdentifier(10L, "2022.3")).thenReturn(false);
        when(studyPlanRepository.findByCareerIdAndStatus(10L, StudyPlanStatus.VIGENTE))
                .thenReturn(Optional.of(currentVigente));
        when(studyPlanRepository.save(any(StudyPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        StudyPlanResponseDTO result = service.create(request(StudyPlanStatus.VIGENTE));

        assertThat(result.getStatus()).isEqualTo(StudyPlanStatus.VIGENTE);
        assertThat(currentVigente.getStatus()).isEqualTo(StudyPlanStatus.NO_VIGENTE);
        verify(studyPlanRepository).save(currentVigente);
    }

    @Test
    @DisplayName("create: un plan VIGENTE se registra igual si la carrera no tenia ninguno vigente")
    void createCurrentPlanShouldWorkWithoutPreviousPlan() {
        when(careerRepository.findById(10L)).thenReturn(Optional.of(career));
        when(studyPlanRepository.existsByCareerIdAndIdentifier(10L, "2022.3")).thenReturn(false);
        when(studyPlanRepository.findByCareerIdAndStatus(10L, StudyPlanStatus.VIGENTE))
                .thenReturn(Optional.empty());
        when(studyPlanRepository.save(any(StudyPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.create(request(StudyPlanStatus.VIGENTE)).getStatus())
                .isEqualTo(StudyPlanStatus.VIGENTE);
    }

    @Test
    @DisplayName("create: rechaza un identificador repetido en la misma carrera")
    void createShouldRejectDuplicateIdentifier() {
        when(careerRepository.findById(10L)).thenReturn(Optional.of(career));
        when(studyPlanRepository.existsByCareerIdAndIdentifier(10L, "2022.3")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request(StudyPlanStatus.NO_VIGENTE)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("identifier '2022.3' already exists for career '10450'");

        verify(studyPlanRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: 404 si la carrera no existe")
    void createShouldFailWhenCareerNotFound() {
        when(careerRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request(StudyPlanStatus.VIGENTE)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Career not found with id: 10");

        verify(studyPlanRepository, never()).save(any());
    }

    // ---------- lecturas ----------

    @Test
    @DisplayName("findById: devuelve el plan con los datos de su carrera")
    void findByIdShouldReturnPlan() {
        when(studyPlanRepository.findById(1L)).thenReturn(Optional.of(existing));

        StudyPlanResponseDTO result = service.findById(1L);

        assertThat(result.getName()).isEqualTo("Plan 2022");
        assertThat(result.getCareerId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("findById: 404 si el plan no existe")
    void findByIdShouldFailWhenNotFound() {
        when(studyPlanRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Study plan not found with id: 999");
    }

    @Test
    @DisplayName("findAll: devuelve todos los planes")
    void findAllShouldReturnPlans() {
        when(studyPlanRepository.findAll()).thenReturn(List.of(existing, currentVigente));

        assertThat(service.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("findByCareer: devuelve los planes de la carrera")
    void findByCareerShouldFilter() {
        when(careerRepository.existsById(10L)).thenReturn(true);
        when(studyPlanRepository.findByCareerId(10L)).thenReturn(List.of(existing, currentVigente));

        assertThat(service.findByCareer(10L)).hasSize(2);
    }

    @Test
    @DisplayName("findByCareer: 404 si la carrera no existe")
    void findByCareerShouldFailWhenCareerNotFound() {
        when(careerRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.findByCareer(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Career not found with id: 99");
    }

    // ---------- update ----------

    @Test
    @DisplayName("update: modifica el nombre del plan")
    void updateShouldModifyPlan() {
        when(studyPlanRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(careerRepository.findById(10L)).thenReturn(Optional.of(career));
        when(studyPlanRepository.save(any(StudyPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        StudyPlanRequestDTO dto = request(StudyPlanStatus.NO_VIGENTE);
        dto.setName("Plan 2022 actualizado");

        StudyPlanResponseDTO result = service.update(1L, dto);

        assertThat(result.getName()).isEqualTo("Plan 2022 actualizado");
        assertThat(result.getStatus()).isEqualTo(StudyPlanStatus.NO_VIGENTE);
    }

    @Test
    @DisplayName("update: permite guardar sin cambiar el identificador")
    void updateShouldAllowSameIdentifier() {
        when(studyPlanRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(careerRepository.findById(10L)).thenReturn(Optional.of(career));
        when(studyPlanRepository.save(any(StudyPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.update(1L, request(StudyPlanStatus.NO_VIGENTE))).isNotNull();

        verify(studyPlanRepository, never()).existsByCareerIdAndIdentifier(anyLong(), anyString());
    }

    @Test
    @DisplayName("update: rechaza un identificador ya usado en la misma carrera")
    void updateShouldRejectDuplicateIdentifier() {
        when(studyPlanRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(careerRepository.findById(10L)).thenReturn(Optional.of(career));
        when(studyPlanRepository.existsByCareerIdAndIdentifier(10L, "2021.3")).thenReturn(true);

        StudyPlanRequestDTO dto = request(StudyPlanStatus.NO_VIGENTE);
        dto.setIdentifier("2021.3");

        assertThatThrownBy(() -> service.update(1L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("identifier '2021.3' already exists for career '10450'");

        verify(studyPlanRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: pasar el plan a VIGENTE deja NO_VIGENTE al anterior")
    void updateToCurrentShouldDeactivatePrevious() {
        when(studyPlanRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(careerRepository.findById(10L)).thenReturn(Optional.of(career));
        when(studyPlanRepository.findByCareerIdAndStatus(10L, StudyPlanStatus.VIGENTE))
                .thenReturn(Optional.of(currentVigente));
        when(studyPlanRepository.save(any(StudyPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        StudyPlanResponseDTO result = service.update(1L, request(StudyPlanStatus.VIGENTE));

        assertThat(result.getStatus()).isEqualTo(StudyPlanStatus.VIGENTE);
        assertThat(currentVigente.getStatus()).isEqualTo(StudyPlanStatus.NO_VIGENTE);
    }

    @Test
    @DisplayName("update: un plan que ya era VIGENTE no se desactiva a si mismo")
    void updateAlreadyCurrentShouldNotDeactivate() {
        when(studyPlanRepository.findById(2L)).thenReturn(Optional.of(currentVigente));
        when(careerRepository.findById(10L)).thenReturn(Optional.of(career));
        when(studyPlanRepository.save(any(StudyPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        StudyPlanRequestDTO dto = request(StudyPlanStatus.VIGENTE);
        dto.setIdentifier("2021.3");

        StudyPlanResponseDTO result = service.update(2L, dto);

        assertThat(result.getStatus()).isEqualTo(StudyPlanStatus.VIGENTE);
        verify(studyPlanRepository, never()).findByCareerIdAndStatus(anyLong(), any());
    }

    @Test
    @DisplayName("update: 404 si el plan no existe")
    void updateShouldFailWhenPlanNotFound() {
        when(studyPlanRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(999L, request(StudyPlanStatus.NO_VIGENTE)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Study plan not found with id: 999");
    }

    @Test
    @DisplayName("update: 404 si la carrera no existe")
    void updateShouldFailWhenCareerNotFound() {
        when(studyPlanRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(careerRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(1L, request(StudyPlanStatus.NO_VIGENTE)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Career not found with id: 10");

        verify(studyPlanRepository, never()).save(any());
    }

    // ---------- changeStatus ----------

    @Test
    @DisplayName("changeStatus: pasar a VIGENTE deja NO_VIGENTE al plan vigente anterior")
    void changeStatusToCurrentShouldDeactivatePrevious() {
        when(studyPlanRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(studyPlanRepository.findByCareerIdAndStatus(10L, StudyPlanStatus.VIGENTE))
                .thenReturn(Optional.of(currentVigente));
        when(studyPlanRepository.save(any(StudyPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        StudyPlanResponseDTO result = service.changeStatus(1L, "VIGENTE");

        assertThat(result.getStatus()).isEqualTo(StudyPlanStatus.VIGENTE);
        assertThat(currentVigente.getStatus()).isEqualTo(StudyPlanStatus.NO_VIGENTE);
    }

    @Test
    @DisplayName("changeStatus: pasar a NO_VIGENTE no toca a los demas planes")
    void changeStatusToNonCurrentShouldNotTouchOthers() {
        when(studyPlanRepository.findById(2L)).thenReturn(Optional.of(currentVigente));
        when(studyPlanRepository.save(any(StudyPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        StudyPlanResponseDTO result = service.changeStatus(2L, "no_vigente");

        assertThat(result.getStatus()).isEqualTo(StudyPlanStatus.NO_VIGENTE);
        verify(studyPlanRepository, never()).findByCareerIdAndStatus(anyLong(), any());
    }

    @Test
    @DisplayName("changeStatus: rechaza un estado invalido")
    void changeStatusShouldRejectInvalidStatus() {
        when(studyPlanRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.changeStatus(1L, "DEROGADO"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid status: DEROGADO");

        verify(studyPlanRepository, never()).save(any());
    }

    @Test
    @DisplayName("changeStatus: 404 si el plan no existe")
    void changeStatusShouldFailWhenNotFound() {
        when(studyPlanRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeStatus(999L, "VIGENTE"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Study plan not found with id: 999");
    }

    // ---------- delete ----------

    @Test
    @DisplayName("delete: elimina un plan sin estudiantes asociados")
    void deleteShouldRemovePlan() {
        when(studyPlanRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(studentRepository.existsByStudyPlanId(1L)).thenReturn(false);

        service.delete(1L);

        verify(studyPlanRepository).delete(existing);
    }

    @Test
    @DisplayName("delete: no elimina un plan con estudiantes asociados")
    void deleteShouldRejectPlanWithStudents() {
        when(studyPlanRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(studentRepository.existsByStudyPlanId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("has students associated and cannot be deleted");

        verify(studyPlanRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete: 404 si el plan no existe")
    void deleteShouldFailWhenNotFound() {
        when(studyPlanRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Study plan not found with id: 999");

        verify(studyPlanRepository, never()).delete(any());
    }
}
