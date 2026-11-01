package com.example.siga.service.impl;

import com.example.siga.dto.GradeRequestDTO;
import com.example.siga.dto.GradeResponseDTO;
import com.example.siga.entity.AcademicPeriod;
import com.example.siga.entity.AcademicPeriodStatus;
import com.example.siga.entity.Enrollment;
import com.example.siga.entity.EnrollmentStatus;
import com.example.siga.entity.Grade;
import com.example.siga.entity.Section;
import com.example.siga.exception.BusinessException;
import com.example.siga.exception.ResourceNotFoundException;
import com.example.siga.mapper.GradeMapper;
import com.example.siga.repository.EnrollmentRepository;
import com.example.siga.repository.GradeRepository;
import com.example.siga.repository.SectionRepository;
import com.example.siga.service.GradeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GradeServiceImpl implements GradeService {

    /** Regla: la escala de notas va de 1.0 a 7.0 con un decimal. */
    private static final BigDecimal MIN_GRADE = new BigDecimal("1.0");
    private static final BigDecimal MAX_GRADE = new BigDecimal("7.0");
    private static final int GRADE_SCALE = 1;

    private final GradeRepository gradeRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final SectionRepository sectionRepository;
    private final GradeMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<GradeResponseDTO> findBySection(Long teacherId, Long sectionId) {
        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Section not found with id: " + sectionId));

        requireResponsibleTeacher(section, teacherId);

        Map<Long, Grade> gradesByEnrollment = gradeRepository.findByEnrollmentSectionId(sectionId).stream()
                .collect(Collectors.toMap(grade -> grade.getEnrollment().getId(), Function.identity()));

        // Las anuladas no se califican, por eso el acta solo trae las INSCRITA
        return enrollmentRepository.findBySectionIdAndStatus(sectionId, EnrollmentStatus.INSCRITA).stream()
                .map(enrollment -> {
                    Grade grade = gradesByEnrollment.get(enrollment.getId());
                    return grade != null ? mapper.toResponseDTO(grade) : mapper.pendingFor(enrollment);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public GradeResponseDTO create(Long teacherId, GradeRequestDTO dto) {
        Enrollment enrollment = findEnrollmentOrThrow(dto.getEnrollmentId());

        // Regla: solo el docente responsable de la seccion califica
        requireResponsibleTeacher(enrollment.getSection(), teacherId);

        // Regla: las notas solo se registran con el periodo ABIERTO
        requireOpenPeriod(enrollment.getAcademicPeriod());

        // Regla: solo se califica a los estudiantes inscritos
        if (enrollment.getStatus() != EnrollmentStatus.INSCRITA) {
            throw new BusinessException(
                    "Enrollment " + enrollment.getId() + " is ANULADA and cannot be graded"
            );
        }

        // Regla: una unica calificacion final por inscripcion
        if (gradeRepository.existsByEnrollmentId(enrollment.getId())) {
            throw new BusinessException(
                    "Enrollment " + enrollment.getId()
                            + " already has a final grade; update it instead of creating a new one"
            );
        }

        Grade grade = Grade.builder()
                .enrollment(enrollment)
                .value(normalize(dto.getValue()))
                .recordedAt(LocalDateTime.now())
                .build();

        return mapper.toResponseDTO(gradeRepository.save(grade));
    }

    @Override
    @Transactional
    public GradeResponseDTO update(Long teacherId, Long id, GradeRequestDTO dto) {
        Grade grade = gradeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Grade not found with id: " + id));

        Enrollment enrollment = grade.getEnrollment();

        // La calificacion no se traspasa de una inscripcion a otra
        if (!enrollment.getId().equals(dto.getEnrollmentId())) {
            throw new BusinessException(
                    "Grade " + id + " belongs to enrollment " + enrollment.getId()
                            + " and cannot be moved to enrollment " + dto.getEnrollmentId()
            );
        }

        requireResponsibleTeacher(enrollment.getSection(), teacherId);

        // Regla: la nota es modificable mientras el periodo esta ABIERTO
        requireOpenPeriod(enrollment.getAcademicPeriod());

        grade.setValue(normalize(dto.getValue()));
        grade.setUpdatedAt(LocalDateTime.now());

        return mapper.toResponseDTO(gradeRepository.save(grade));
    }

    private Enrollment findEnrollmentOrThrow(Long enrollmentId) {
        return enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment not found with id: " + enrollmentId));
    }

    /** Un teacherId nulo es el ADMIN, que consulta cualquier acta. */
    private void requireResponsibleTeacher(Section section, Long teacherId) {
        if (teacherId == null) {
            return;
        }
        if (!section.getTeacher().getId().equals(teacherId)) {
            throw new BusinessException(
                    "Teacher " + teacherId + " is not responsible for the section of subject '"
                            + section.getSubject().getCode() + "'"
            );
        }
    }

    private void requireOpenPeriod(AcademicPeriod period) {
        if (period.getStatus() != AcademicPeriodStatus.ABIERTO) {
            throw new BusinessException(
                    "Academic period '" + period.getCode() + "' is CERRADO: grades cannot be changed"
            );
        }
    }

    /**
     * La columna guarda un decimal: el valor se redondea antes de persistirlo y el
     * rango se revalida aqui porque el servicio tambien se usa fuera del controlador.
     */
    private BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            throw new BusinessException("Grade value is required");
        }
        BigDecimal rounded = value.setScale(GRADE_SCALE, RoundingMode.HALF_UP);
        if (rounded.compareTo(MIN_GRADE) < 0 || rounded.compareTo(MAX_GRADE) > 0) {
            throw new BusinessException(
                    "Grade value must be between " + MIN_GRADE + " and " + MAX_GRADE + " (" + value + " given)"
            );
        }
        return rounded;
    }
}
