package com.example.siga.service.impl;

import com.example.siga.dto.CareerRequestDTO;
import com.example.siga.dto.CareerResponseDTO;
import com.example.siga.entity.Career;
import com.example.siga.entity.CareerStatus;
import com.example.siga.exception.BusinessException;
import com.example.siga.exception.ResourceNotFoundException;
import com.example.siga.mapper.CareerMapper;
import com.example.siga.repository.CareerRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CareerServiceImplTest {

    @Mock
    private CareerRepository careerRepository;

    @Mock
    private StudyPlanRepository studyPlanRepository;

    @Mock
    private StudentRepository studentRepository;

    private CareerServiceImpl service;

    private Career existing;

    @BeforeEach
    void setUp() {
        service = new CareerServiceImpl(
                careerRepository, studyPlanRepository, studentRepository, new CareerMapper());

        existing = Career.builder()
                .id(1L)
                .code("10450")
                .name("Tecnico Analista Programador")
                .description("Carrera tecnica de cuatro semestres")
                .status(CareerStatus.ACTIVA)
                .durationSemesters(4)
                .build();
    }

    private CareerRequestDTO request() {
        return CareerRequestDTO.builder()
                .code("10450")
                .name("Tecnico Analista Programador")
                .description("Carrera tecnica de cuatro semestres")
                .status(CareerStatus.ACTIVA)
                .build();
    }

    // ---------- create ----------

    @Test
    @DisplayName("create: registra la carrera con duracion de 4 semestres")
    void createShouldForceFourSemesters() {
        when(careerRepository.existsByCode("10450")).thenReturn(false);
        when(careerRepository.save(any(Career.class))).thenAnswer(inv -> {
            Career saved = inv.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        CareerResponseDTO result = service.create(request());

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getCode()).isEqualTo("10450");
        assertThat(result.getStatus()).isEqualTo(CareerStatus.ACTIVA);
        assertThat(result.getDurationSemesters()).isEqualTo(4);

        ArgumentCaptor<Career> captor = ArgumentCaptor.forClass(Career.class);
        verify(careerRepository).save(captor.capture());
        assertThat(captor.getValue().getDurationSemesters()).isEqualTo(4);
    }

    @Test
    @DisplayName("create: permite registrar una carrera INACTIVA")
    void createShouldAcceptInactiveStatus() {
        when(careerRepository.existsByCode("10450")).thenReturn(false);
        when(careerRepository.save(any(Career.class))).thenAnswer(inv -> inv.getArgument(0));

        CareerRequestDTO dto = request();
        dto.setStatus(CareerStatus.INACTIVA);

        assertThat(service.create(dto).getStatus()).isEqualTo(CareerStatus.INACTIVA);
    }

    @Test
    @DisplayName("create: rechaza un codigo ya registrado")
    void createShouldRejectDuplicateCode() {
        when(careerRepository.existsByCode("10450")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("code '10450' already exists");

        verify(careerRepository, never()).save(any());
    }

    // ---------- lecturas ----------

    @Test
    @DisplayName("findById: devuelve la carrera")
    void findByIdShouldReturnCareer() {
        when(careerRepository.findById(1L)).thenReturn(Optional.of(existing));

        CareerResponseDTO result = service.findById(1L);

        assertThat(result.getCode()).isEqualTo("10450");
        assertThat(result.getName()).isEqualTo("Tecnico Analista Programador");
        assertThat(result.getDurationSemesters()).isEqualTo(4);
    }

    @Test
    @DisplayName("findById: 404 si la carrera no existe")
    void findByIdShouldFailWhenNotFound() {
        when(careerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Career not found with id: 999");
    }

    @Test
    @DisplayName("findAll: devuelve todas las carreras")
    void findAllShouldReturnCareers() {
        when(careerRepository.findAll()).thenReturn(List.of(existing));

        assertThat(service.findAll()).hasSize(1);
    }

    // ---------- update ----------

    @Test
    @DisplayName("update: modifica nombre, descripcion y estado sin tocar la duracion")
    void updateShouldModifyCareer() {
        when(careerRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(careerRepository.save(any(Career.class))).thenAnswer(inv -> inv.getArgument(0));

        CareerRequestDTO dto = request();
        dto.setName("Tecnico Analista Programador (v2)");
        dto.setDescription("Descripcion actualizada");
        dto.setStatus(CareerStatus.INACTIVA);

        CareerResponseDTO result = service.update(1L, dto);

        assertThat(result.getName()).isEqualTo("Tecnico Analista Programador (v2)");
        assertThat(result.getDescription()).isEqualTo("Descripcion actualizada");
        assertThat(result.getStatus()).isEqualTo(CareerStatus.INACTIVA);
        assertThat(result.getDurationSemesters()).isEqualTo(4);
    }

    @Test
    @DisplayName("update: permite guardar sin cambiar el codigo")
    void updateShouldAllowSameCode() {
        when(careerRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(careerRepository.save(any(Career.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.update(1L, request())).isNotNull();

        verify(careerRepository, never()).existsByCode(anyString());
    }

    @Test
    @DisplayName("update: cambia el codigo a uno libre")
    void updateShouldChangeCodeWhenFree() {
        when(careerRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(careerRepository.existsByCode("10455")).thenReturn(false);
        when(careerRepository.save(any(Career.class))).thenAnswer(inv -> inv.getArgument(0));

        CareerRequestDTO dto = request();
        dto.setCode("10455");

        assertThat(service.update(1L, dto).getCode()).isEqualTo("10455");
    }

    @Test
    @DisplayName("update: rechaza cambiar el codigo a uno ya registrado")
    void updateShouldRejectDuplicateCode() {
        when(careerRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(careerRepository.existsByCode("10451")).thenReturn(true);

        CareerRequestDTO dto = request();
        dto.setCode("10451");

        assertThatThrownBy(() -> service.update(1L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("code '10451' already exists");

        verify(careerRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: 404 si la carrera no existe")
    void updateShouldFailWhenNotFound() {
        when(careerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(999L, request()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Career not found with id: 999");

        verify(careerRepository, never()).save(any());
    }

    // ---------- delete ----------

    @Test
    @DisplayName("delete: elimina una carrera sin estudiantes ni planes")
    void deleteShouldRemoveCareer() {
        when(careerRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(studentRepository.existsByCareerId(1L)).thenReturn(false);
        when(studyPlanRepository.existsByCareerId(1L)).thenReturn(false);

        service.delete(1L);

        verify(careerRepository).delete(existing);
    }

    @Test
    @DisplayName("delete: no elimina una carrera con estudiantes")
    void deleteShouldRejectCareerWithStudents() {
        when(careerRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(studentRepository.existsByCareerId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("has students associated and cannot be deleted");

        verify(careerRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete: no elimina una carrera con planes de estudio")
    void deleteShouldRejectCareerWithStudyPlans() {
        when(careerRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(studentRepository.existsByCareerId(1L)).thenReturn(false);
        when(studyPlanRepository.existsByCareerId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("has study plans associated and cannot be deleted");

        verify(careerRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete: 404 si la carrera no existe")
    void deleteShouldFailWhenNotFound() {
        when(careerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Career not found with id: 999");

        verify(careerRepository, never()).delete(any());
    }
}
