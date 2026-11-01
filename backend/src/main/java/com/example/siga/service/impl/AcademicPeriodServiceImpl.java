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
import com.example.siga.service.AcademicPeriodService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AcademicPeriodServiceImpl implements AcademicPeriodService {

    private final AcademicPeriodRepository academicPeriodRepository;
    private final SectionRepository sectionRepository;
    private final StudentPeriodStatusRepository studentPeriodStatusRepository;
    private final AcademicClosureService academicClosureService;
    private final AcademicPeriodMapper mapper;

    @Override
    @Transactional
    public AcademicPeriodResponseDTO create(AcademicPeriodRequestDTO dto) {
        // Regla: un unico periodo por combinacion de anio y semestre
        requireYearAndSemesterFree(dto.getYear(), dto.getSemester());

        AcademicPeriod period = mapper.toEntity(dto);
        period.setCode(buildCode(dto.getYear(), dto.getSemester()));

        // Regla: todo periodo academico nace ABIERTO
        period.setStatus(AcademicPeriodStatus.ABIERTO);

        return mapper.toResponseDTO(academicPeriodRepository.save(period));
    }

    @Override
    @Transactional(readOnly = true)
    public AcademicPeriodResponseDTO findById(Long id) {
        return mapper.toResponseDTO(findPeriodOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AcademicPeriodResponseDTO> findAll() {
        return academicPeriodRepository.findAll().stream()
                .map(mapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AcademicPeriodResponseDTO> findByStatus(String status) {
        return academicPeriodRepository.findByStatus(parseStatus(status)).stream()
                .map(mapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AcademicPeriodResponseDTO update(Long id, AcademicPeriodRequestDTO dto) {
        AcademicPeriod period = findPeriodOrThrow(id);

        // Regla: un periodo CERRADO ya no se modifica
        requireOpen(period);

        // Regla: el anio y semestre no pueden quedar iguales a los de otro periodo
        if (!(period.getYear().equals(dto.getYear()) && period.getSemester().equals(dto.getSemester()))) {
            requireYearAndSemesterFree(dto.getYear(), dto.getSemester());
        }

        mapper.updateEntity(period, dto);
        period.setCode(buildCode(dto.getYear(), dto.getSemester()));

        return mapper.toResponseDTO(academicPeriodRepository.save(period));
    }

    @Override
    @Transactional
    public AcademicPeriodResponseDTO changeStatus(Long id, String newStatus) {
        AcademicPeriod period = findPeriodOrThrow(id);

        AcademicPeriodStatus status = parseStatus(newStatus);

        // Regla: ABIERTO -> CERRADO no tiene retorno
        if (period.getStatus() == AcademicPeriodStatus.CERRADO) {
            throw new BusinessException(
                    "Academic period '" + period.getCode() + "' is already CERRADO and cannot be reopened"
            );
        }

        // Cerrar no es solo cambiar el estado: exige todas las notas registradas y
        // consolida el historial academico, asi que se delega al cierre de periodo.
        if (status == AcademicPeriodStatus.CERRADO) {
            academicClosureService.close(id);
            return mapper.toResponseDTO(findPeriodOrThrow(id));
        }

        period.setStatus(status);
        return mapper.toResponseDTO(academicPeriodRepository.save(period));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        AcademicPeriod period = findPeriodOrThrow(id);

        // Regla: un periodo CERRADO es informacion historica y no se elimina
        requireOpen(period);

        // Regla: un periodo con secciones no se elimina
        if (sectionRepository.existsByAcademicPeriodId(id)) {
            throw new BusinessException(
                    "Academic period '" + period.getCode() + "' has sections associated and cannot be deleted"
            );
        }

        // Las matriculas registradas apuntan al periodo: borrarlo las dejaria huerfanas
        if (studentPeriodStatusRepository.existsByAcademicPeriodId(id)) {
            throw new BusinessException(
                    "Academic period '" + period.getCode()
                            + "' has matriculation records and cannot be deleted"
            );
        }

        academicPeriodRepository.delete(period);
    }

    private AcademicPeriod findPeriodOrThrow(Long id) {
        return academicPeriodRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Academic period not found with id: " + id));
    }

    private void requireOpen(AcademicPeriod period) {
        if (period.getStatus() != AcademicPeriodStatus.ABIERTO) {
            throw new BusinessException(
                    "Academic period '" + period.getCode() + "' is CERRADO and cannot be modified"
            );
        }
    }

    private void requireYearAndSemesterFree(Integer year, Integer semester) {
        if (academicPeriodRepository.existsByYearAndSemester(year, semester)) {
            throw new BusinessException(
                    "An academic period '" + buildCode(year, semester) + "' already exists"
            );
        }
    }

    private String buildCode(Integer year, Integer semester) {
        return year + "-" + semester;
    }

    private AcademicPeriodStatus parseStatus(String status) {
        if (status == null) {
            throw new BusinessException("Status is required");
        }
        try {
            return AcademicPeriodStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Invalid status: " + status);
        }
    }
}
