package com.example.siga.service.impl;

import com.example.siga.dto.SubjectRequestDTO;
import com.example.siga.dto.SubjectResponseDTO;
import com.example.siga.entity.Prerequisite;
import com.example.siga.entity.StudyPlan;
import com.example.siga.entity.Subject;
import com.example.siga.exception.BusinessException;
import com.example.siga.exception.ResourceNotFoundException;
import com.example.siga.mapper.SubjectMapper;
import com.example.siga.repository.AcademicHistoryRepository;
import com.example.siga.repository.EnrollmentRepository;
import com.example.siga.repository.PrerequisiteRepository;
import com.example.siga.repository.SectionRepository;
import com.example.siga.repository.StudyPlanRepository;
import com.example.siga.repository.SubjectRepository;
import com.example.siga.service.SubjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubjectServiceImpl implements SubjectService {
    private final SubjectRepository subjectRepository;
    private final StudyPlanRepository studyPlanRepository;
    private final PrerequisiteRepository prerequisiteRepository;
    private final SectionRepository sectionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AcademicHistoryRepository academicHistoryRepository;
    private final SubjectMapper mapper;

    @Override
    @Transactional
    public SubjectResponseDTO create(SubjectRequestDTO dto) {
        StudyPlan studyPlan = studyPlanRepository.findById(dto.getStudyPlanId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Study plan not found with id: " + dto.getStudyPlanId()));

        // Regla: código único dentro del mismo plan de estudios
        if (subjectRepository.existsByStudyPlanIdAndCode(studyPlan.getId(), dto.getCode())) {
            throw new BusinessException(
                    "A subject with code '" + dto.getCode()
                            + "' already exists in study plan '" + studyPlan.getIdentifier() + "'"
            );
        }

        Subject subject = mapper.toEntity(dto);
        subject.setStudyPlan(studyPlan);

        return mapper.toResponseDTO(subjectRepository.save(subject));
    }

    @Override
    @Transactional(readOnly = true)
    public SubjectResponseDTO findById(Long id) {
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subject not found with id: " + id));
        return mapper.toResponseDTO(subject);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubjectResponseDTO> findAll() {
        return subjectRepository.findAll().stream()
                .map(mapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubjectResponseDTO> findByStudyPlan(Long studyPlanId) {
        if (!studyPlanRepository.existsById(studyPlanId)) {
            throw new ResourceNotFoundException("Study plan not found with id: " + studyPlanId);
        }
        return subjectRepository.findByStudyPlanId(studyPlanId).stream()
                .map(mapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public SubjectResponseDTO update(Long id, SubjectRequestDTO dto) {
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subject not found with id: " + id));

        StudyPlan studyPlan = studyPlanRepository.findById(dto.getStudyPlanId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Study plan not found with id: " + dto.getStudyPlanId()));

        // Regla: no cambiar el código a uno ya existente en el mismo plan.
        // Tambien aplica si cambia el plan, porque el codigo debe ser unico en el destino.
        boolean studyPlanChanged = !subject.getStudyPlan().getId().equals(studyPlan.getId());
        if ((!subject.getCode().equals(dto.getCode()) || studyPlanChanged)
                && subjectRepository.existsByStudyPlanIdAndCode(studyPlan.getId(), dto.getCode())) {
            throw new BusinessException(
                    "A subject with code '" + dto.getCode()
                            + "' already exists in study plan '" + studyPlan.getIdentifier() + "'"
            );
        }

        // Regla: mover la asignatura a otro plan dejaria prerrequisitos de planes distintos
        if (studyPlanChanged
                && (prerequisiteRepository.countBySubjectId(id) > 0
                || prerequisiteRepository.existsByPrerequisiteSubjectId(id))) {
            throw new BusinessException(
                    "Subject '" + subject.getCode() + "' cannot be moved to another study plan"
                            + " while it has prerequisites associated"
            );
        }

        // Regla: los prerrequisitos deben seguir siendo de semestres anteriores
        if (!subject.getSemester().equals(dto.getSemester())) {
            requireSemesterKeepsPrerequisitesValid(subject, dto.getSemester());
        }

        mapper.updateEntity(subject, dto);
        subject.setStudyPlan(studyPlan);

        return mapper.toResponseDTO(subjectRepository.save(subject));
    }

    /**
     * Comprueba que cambiar la asignatura al semestre indicado no invalide los
     * prerrequisitos ya registrados, ni los propios ni los de las asignaturas
     * que dependen de ella.
     */
    private void requireSemesterKeepsPrerequisitesValid(Subject subject, int newSemester) {
        for (Prerequisite own : prerequisiteRepository.findBySubjectId(subject.getId())) {
            Subject required = own.getPrerequisiteSubject();
            if (required.getSemester() >= newSemester) {
                throw new BusinessException(
                        "Subject '" + subject.getCode() + "' cannot be moved to semester " + newSemester
                                + " because its prerequisite '" + required.getCode() + "' belongs to semester "
                                + required.getSemester()
                );
            }
        }

        for (Prerequisite dependent : prerequisiteRepository.findByPrerequisiteSubjectId(subject.getId())) {
            Subject other = dependent.getSubject();
            if (newSemester >= other.getSemester()) {
                throw new BusinessException(
                        "Subject '" + subject.getCode() + "' cannot be moved to semester " + newSemester
                                + " because it is a prerequisite of '" + other.getCode() + "' (semester "
                                + other.getSemester() + ")"
                );
            }
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subject not found with id: " + id));

        // Regla: no se puede eliminar una asignatura que esté en el historial académico.
        // El historial guarda el código como snapshot, así que la comprobación es por código.
        if (academicHistoryRepository.existsBySubjectCode(subject.getCode())) {
            throw new BusinessException(
                    "Subject '" + subject.getCode() + "' appears in the academic history and cannot be deleted"
            );
        }

        // Regla: una asignatura con inscripciones no se elimina
        if (enrollmentRepository.existsBySubjectId(id)) {
            throw new BusinessException(
                    "Subject '" + subject.getCode() + "' has enrollments and cannot be deleted"
            );
        }

        // Regla: una asignatura con secciones en la oferta academica no se elimina
        if (sectionRepository.existsBySubjectId(id)) {
            throw new BusinessException(
                    "Subject '" + subject.getCode() + "' has sections associated and cannot be deleted"
            );
        }

        // Regla: no se puede eliminar una asignatura que es prerrequisito de otra
        if (prerequisiteRepository.existsByPrerequisiteSubjectId(id)) {
            throw new BusinessException(
                    "Subject '" + subject.getCode() + "' cannot be deleted because it is a prerequisite of another subject"
            );
        }

        // Sus propios prerrequisitos se eliminan junto con la asignatura
        prerequisiteRepository.deleteBySubjectId(id);
        subjectRepository.delete(subject);
    }
}
