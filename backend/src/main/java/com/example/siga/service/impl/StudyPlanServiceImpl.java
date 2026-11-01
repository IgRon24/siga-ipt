package com.example.siga.service.impl;

import com.example.siga.dto.StudyPlanRequestDTO;
import com.example.siga.dto.StudyPlanResponseDTO;
import com.example.siga.entity.Career;
import com.example.siga.entity.StudyPlan;
import com.example.siga.entity.StudyPlanStatus;
import com.example.siga.exception.BusinessException;
import com.example.siga.exception.ResourceNotFoundException;
import com.example.siga.mapper.StudyPlanMapper;
import com.example.siga.repository.CareerRepository;
import com.example.siga.repository.StudentRepository;
import com.example.siga.repository.StudyPlanRepository;
import com.example.siga.service.StudyPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudyPlanServiceImpl implements StudyPlanService {

    private final StudyPlanRepository studyPlanRepository;
    private final CareerRepository careerRepository;
    private final StudentRepository studentRepository;
    private final StudyPlanMapper mapper;

    @Override
    @Transactional
    public StudyPlanResponseDTO create(StudyPlanRequestDTO dto) {
        Career career = careerRepository.findById(dto.getCareerId())
                .orElseThrow(() -> new ResourceNotFoundException("Career not found with id: " + dto.getCareerId()));

        // Regla: identificador único dentro de la misma carrera
        if (studyPlanRepository.existsByCareerIdAndIdentifier(career.getId(), dto.getIdentifier())) {
            throw new BusinessException(
                    "A study plan with identifier '" + dto.getIdentifier()
                            + "' already exists for career '" + career.getCode() + "'"
            );
        }

        StudyPlan plan = mapper.toEntity(dto);
        plan.setCareer(career);

        // Regla clave: si el nuevo plan es VIGENTE, el anterior pasa a NO_VIGENTE
        if (dto.getStatus() == StudyPlanStatus.VIGENTE) {
            deactivateCurrentPlan(career.getId());
        }

        return mapper.toResponseDTO(studyPlanRepository.save(plan));
    }

    @Override
    @Transactional(readOnly = true)
    public StudyPlanResponseDTO findById(Long id) {
        StudyPlan plan = studyPlanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Study plan not found with id: " + id));
        return mapper.toResponseDTO(plan);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudyPlanResponseDTO> findAll() {
        return studyPlanRepository.findAll().stream()
                .map(mapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudyPlanResponseDTO> findByCareer(Long careerId) {
        if (!careerRepository.existsById(careerId)) {
            throw new ResourceNotFoundException("Career not found with id: " + careerId);
        }
        return studyPlanRepository.findByCareerId(careerId).stream()
                .map(mapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public StudyPlanResponseDTO update(Long id, StudyPlanRequestDTO dto) {
        StudyPlan plan = studyPlanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Study plan not found with id: " + id));

        Career career = careerRepository.findById(dto.getCareerId())
                .orElseThrow(() -> new ResourceNotFoundException("Career not found with id: " + dto.getCareerId()));

        // Regla: no se puede cambiar el identificador a uno ya existente en la misma carrera
        if (!plan.getIdentifier().equals(dto.getIdentifier())
                && studyPlanRepository.existsByCareerIdAndIdentifier(career.getId(), dto.getIdentifier())) {
            throw new BusinessException(
                    "A study plan with identifier '" + dto.getIdentifier()
                            + "' already exists for career '" + career.getCode() + "'"
            );
        }

        // Regla: si el plan pasa a VIGENTE, el anterior pasa a NO_VIGENTE
        if (dto.getStatus() == StudyPlanStatus.VIGENTE
                && plan.getStatus() != StudyPlanStatus.VIGENTE) {
            deactivateCurrentPlan(career.getId());
        }

        mapper.updateEntity(plan, dto);
        plan.setCareer(career);

        return mapper.toResponseDTO(studyPlanRepository.save(plan));
    }

    @Override
    @Transactional
    public StudyPlanResponseDTO changeStatus(Long id, String newStatus) {
        StudyPlan plan = studyPlanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Study plan not found with id: " + id));

        StudyPlanStatus status;
        try {
            status = StudyPlanStatus.valueOf(newStatus.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Invalid status: " + newStatus);
        }

        if (status == StudyPlanStatus.VIGENTE) {
            deactivateCurrentPlan(plan.getCareer().getId());
        }

        plan.setStatus(status);
        return mapper.toResponseDTO(studyPlanRepository.save(plan));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        StudyPlan plan = studyPlanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Study plan not found with id: " + id));

        // Regla: no se puede eliminar un plan asociado a estudiantes
        if (studentRepository.existsByStudyPlanId(id)) {
            throw new BusinessException(
                    "Study plan '" + plan.getIdentifier() + "' has students associated and cannot be deleted"
            );
        }

        studyPlanRepository.delete(plan);
    }

    /**
     * Desactiva el plan actualmente VIGENTE de una carrera.
     * Se usa cuando se crea o activa un nuevo plan.
     */
    private void deactivateCurrentPlan(Long careerId) {
        Optional<StudyPlan> currentVigente =
                studyPlanRepository.findByCareerIdAndStatus(careerId, StudyPlanStatus.VIGENTE);

        currentVigente.ifPresent(p -> {
            p.setStatus(StudyPlanStatus.NO_VIGENTE);
            studyPlanRepository.save(p);
        });
    }
}
