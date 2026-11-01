package com.example.siga.service.impl;

import com.example.siga.dto.PrerequisiteRequestDTO;
import com.example.siga.dto.PrerequisiteResponseDTO;
import com.example.siga.entity.Prerequisite;
import com.example.siga.entity.Subject;
import com.example.siga.exception.BusinessException;
import com.example.siga.exception.ResourceNotFoundException;
import com.example.siga.mapper.PrerequisiteMapper;
import com.example.siga.repository.PrerequisiteRepository;
import com.example.siga.repository.SubjectRepository;
import com.example.siga.service.PrerequisiteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PrerequisiteServiceImpl implements PrerequisiteService {

    /** Regla: una asignatura puede tener entre 0 y 3 prerrequisitos. */
    private static final int MAX_PREREQUISITES = 3;

    private final PrerequisiteRepository prerequisiteRepository;
    private final SubjectRepository subjectRepository;
    private final PrerequisiteMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<PrerequisiteResponseDTO> findBySubject(Long subjectId) {
        if (!subjectRepository.existsById(subjectId)) {
            throw new ResourceNotFoundException("Subject not found with id: " + subjectId);
        }
        return prerequisiteRepository.findBySubjectId(subjectId).stream()
                .map(mapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PrerequisiteResponseDTO add(Long subjectId, PrerequisiteRequestDTO dto) {
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new ResourceNotFoundException("Subject not found with id: " + subjectId));

        Long prerequisiteSubjectId = dto.getPrerequisiteSubjectId();

        // Regla: una asignatura no puede ser prerrequisito de sí misma
        if (subjectId.equals(prerequisiteSubjectId)) {
            throw new BusinessException("A subject cannot be a prerequisite of itself");
        }

        Subject prerequisiteSubject = subjectRepository.findById(prerequisiteSubjectId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Subject not found with id: " + prerequisiteSubjectId));

        // Regla: el prerrequisito debe pertenecer al mismo plan de estudios
        Long studyPlanId = subject.getStudyPlan().getId();
        if (!studyPlanId.equals(prerequisiteSubject.getStudyPlan().getId())) {
            throw new BusinessException(
                    "Prerequisite '" + prerequisiteSubject.getCode()
                            + "' must belong to the same study plan as subject '" + subject.getCode() + "'"
            );
        }

        // Regla: el prerrequisito debe ser de un semestre anterior
        if (prerequisiteSubject.getSemester() >= subject.getSemester()) {
            throw new BusinessException(
                    "Prerequisite '" + prerequisiteSubject.getCode() + "' (semester "
                            + prerequisiteSubject.getSemester() + ") must belong to a semester before subject '"
                            + subject.getCode() + "' (semester " + subject.getSemester() + ")"
            );
        }

        // Regla: no se puede repetir el mismo prerrequisito
        if (prerequisiteRepository.existsBySubjectIdAndPrerequisiteSubjectId(subjectId, prerequisiteSubjectId)) {
            throw new BusinessException(
                    "Subject '" + subject.getCode() + "' already has '"
                            + prerequisiteSubject.getCode() + "' as a prerequisite"
            );
        }

        // Regla: máximo 3 prerrequisitos por asignatura
        if (prerequisiteRepository.countBySubjectId(subjectId) >= MAX_PREREQUISITES) {
            throw new BusinessException(
                    "Subject '" + subject.getCode() + "' cannot have more than "
                            + MAX_PREREQUISITES + " prerequisites"
            );
        }

        Prerequisite prerequisite = mapper.toEntity(subject, prerequisiteSubject);
        return mapper.toResponseDTO(prerequisiteRepository.save(prerequisite));
    }

    @Override
    @Transactional
    public void remove(Long subjectId, Long prerequisiteSubjectId) {
        if (!subjectRepository.existsById(subjectId)) {
            throw new ResourceNotFoundException("Subject not found with id: " + subjectId);
        }

        Prerequisite prerequisite = prerequisiteRepository
                .findBySubjectIdAndPrerequisiteSubjectId(subjectId, prerequisiteSubjectId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Subject with id " + subjectId + " has no prerequisite with subject id "
                                + prerequisiteSubjectId));

        prerequisiteRepository.delete(prerequisite);
    }
}
