package com.example.siga.service.impl;

import com.example.siga.dto.AvailableSectionResponseDTO;
import com.example.siga.dto.EnrollmentRequestDTO;
import com.example.siga.dto.EnrollmentResponseDTO;
import com.example.siga.entity.AcademicHistory;
import com.example.siga.entity.AcademicHistoryResult;
import com.example.siga.entity.AcademicPeriod;
import com.example.siga.entity.AcademicPeriodStatus;
import com.example.siga.entity.Enrollment;
import com.example.siga.entity.EnrollmentStatus;
import com.example.siga.entity.MatriculationStatus;
import com.example.siga.entity.Prerequisite;
import com.example.siga.entity.ScheduleBlock;
import com.example.siga.entity.Section;
import com.example.siga.entity.Student;
import com.example.siga.entity.StudentStatus;
import com.example.siga.entity.Subject;
import com.example.siga.exception.BusinessException;
import com.example.siga.exception.ResourceNotFoundException;
import com.example.siga.mapper.EnrollmentMapper;
import com.example.siga.repository.AcademicHistoryRepository;
import com.example.siga.repository.AcademicPeriodRepository;
import com.example.siga.repository.EnrollmentRepository;
import com.example.siga.repository.GradeRepository;
import com.example.siga.repository.PrerequisiteRepository;
import com.example.siga.repository.SectionRepository;
import com.example.siga.repository.StudentPeriodStatusRepository;
import com.example.siga.repository.StudentRepository;
import com.example.siga.service.EnrollmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EnrollmentServiceImpl implements EnrollmentService {

    /** Minimo y maximo de asignaturas por periodo academico. */
    private static final int MIN_SUBJECTS = 3;
    private static final int MAX_SUBJECTS = 6;

    private final EnrollmentRepository enrollmentRepository;
    private final StudentRepository studentRepository;
    private final AcademicPeriodRepository academicPeriodRepository;
    private final SectionRepository sectionRepository;
    private final PrerequisiteRepository prerequisiteRepository;
    private final AcademicHistoryRepository academicHistoryRepository;
    private final StudentPeriodStatusRepository studentPeriodStatusRepository;
    private final GradeRepository gradeRepository;
    private final EnrollmentMapper mapper;

    @Override
    @Transactional
    public List<EnrollmentResponseDTO> enroll(Long studentId, EnrollmentRequestDTO dto) {
        Student student = findStudentOrThrow(studentId);

        // Regla: el estudiante debe estar REGULAR
        if (student.getStatus() != StudentStatus.REGULAR) {
            throw new BusinessException(
                    "Student '" + student.getRun() + "' is " + student.getStatus()
                            + " and cannot enroll subjects"
            );
        }

        AcademicPeriod period = findPeriodOrThrow(dto.getAcademicPeriodId());

        // Regla: las inscripciones solo se hacen con el periodo ABIERTO
        requireOpenPeriod(period);

        // Regla: el estudiante debe estar matriculado en el periodo
        if (!studentPeriodStatusRepository.existsByStudentIdAndAcademicPeriodIdAndEnrollmentStatus(
                studentId, period.getId(), MatriculationStatus.MATRICULATED)) {
            throw new BusinessException(
                    "Student '" + student.getRun() + "' is not MATRICULATED in academic period '"
                            + period.getCode() + "'"
            );
        }

        Set<Long> requestedSectionIds = new LinkedHashSet<>(dto.getSectionIds());
        if (requestedSectionIds.size() != dto.getSectionIds().size()) {
            throw new BusinessException("The same section cannot be enrolled twice");
        }

        List<Enrollment> currentEnrollments = enrollmentRepository
                .findByStudentIdAndAcademicPeriodIdAndStatus(studentId, period.getId(), EnrollmentStatus.INSCRITA);
        Set<Long> enrolledSubjectIds = currentEnrollments.stream()
                .map(enrollment -> enrollment.getSubject().getId())
                .collect(Collectors.toSet());

        Set<String> approvedCodes = approvedSubjectCodes(studentId);
        List<Section> candidates = candidateSections(student, period, approvedCodes, enrolledSubjectIds);

        List<Section> selected = resolveSelectedSections(
                requestedSectionIds, student, period, approvedCodes, enrolledSubjectIds);

        requireLowerSemestersFirst(selected, candidates);
        requireAvailableSeats(selected);
        requireNoScheduleConflict(currentEnrollments, selected);
        requireSubjectCountInRange(currentEnrollments.size(), selected.size(), candidates.size());

        List<Enrollment> enrollments = selected.stream()
                .map(section -> Enrollment.builder()
                        .student(student)
                        .subject(section.getSubject())
                        .section(section)
                        .academicPeriod(period)
                        .enrollmentDate(LocalDateTime.now())
                        .status(EnrollmentStatus.INSCRITA)
                        .build())
                .collect(Collectors.toList());

        return enrollmentRepository.saveAll(enrollments).stream()
                .map(mapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AvailableSectionResponseDTO> findAvailable(Long studentId, Long academicPeriodId) {
        Student student = findStudentOrThrow(studentId);
        AcademicPeriod period = findPeriodOrThrow(academicPeriodId);
        requireOpenPeriod(period);

        Set<Long> enrolledSubjectIds = enrollmentRepository
                .findByStudentIdAndAcademicPeriodIdAndStatus(studentId, period.getId(), EnrollmentStatus.INSCRITA)
                .stream()
                .map(enrollment -> enrollment.getSubject().getId())
                .collect(Collectors.toSet());

        return candidateSections(student, period, approvedSubjectCodes(studentId), enrolledSubjectIds).stream()
                .map(section -> mapper.toAvailableDTO(
                        section,
                        enrollmentRepository.countBySectionIdAndStatus(section.getId(), EnrollmentStatus.INSCRITA)))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnrollmentResponseDTO> findByStudent(Long studentId) {
        if (!studentRepository.existsById(studentId)) {
            throw new ResourceNotFoundException("Student not found with id: " + studentId);
        }
        return enrollmentRepository.findByStudentId(studentId).stream()
                .map(mapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void cancel(Long studentId, Long enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findByIdAndStudentId(enrollmentId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Enrollment not found with id: " + enrollmentId + " for student " + studentId));

        // Regla: las anulaciones solo se hacen con el periodo ABIERTO
        requireOpenPeriod(enrollment.getAcademicPeriod());

        if (enrollment.getStatus() == EnrollmentStatus.ANULADA) {
            throw new BusinessException("Enrollment " + enrollmentId + " is already ANULADA");
        }

        // Una nota ya registrada es un resultado academico: anularla dejaria la
        // calificacion colgando de una inscripcion que no pasa al historial.
        if (gradeRepository.existsByEnrollmentId(enrollmentId)) {
            throw new BusinessException(
                    "Enrollment " + enrollmentId + " has a final grade recorded and cannot be cancelled"
            );
        }

        // La anulacion libera el cupo: los conteos solo miran las INSCRITA
        enrollment.setStatus(EnrollmentStatus.ANULADA);
        enrollmentRepository.save(enrollment);
    }

    /**
     * Asignaturas del plan del estudiante ofrecidas en el periodo que puede inscribir:
     * no aprobadas, no inscritas ya en el periodo y con sus prerrequisitos aprobados.
     * Los cupos no entran en este filtro; una seccion llena aparece con 0 disponibles.
     */
    private List<Section> candidateSections(Student student,
                                            AcademicPeriod period,
                                            Set<String> approvedCodes,
                                            Set<Long> enrolledSubjectIds) {
        Long studyPlanId = student.getStudyPlan().getId();

        return sectionRepository.findByAcademicPeriodId(period.getId()).stream()
                .filter(section -> section.getSubject().getStudyPlan().getId().equals(studyPlanId))
                .filter(section -> !approvedCodes.contains(section.getSubject().getCode()))
                .filter(section -> !enrolledSubjectIds.contains(section.getSubject().getId()))
                .filter(section -> prerequisitesApproved(section.getSubject(), approvedCodes))
                .sorted(Comparator.comparing(section -> section.getSubject().getSemester()))
                .collect(Collectors.toList());
    }

    private List<Section> resolveSelectedSections(Set<Long> sectionIds,
                                                  Student student,
                                                  AcademicPeriod period,
                                                  Set<String> approvedCodes,
                                                  Set<Long> enrolledSubjectIds) {
        List<Section> selected = new ArrayList<>();

        for (Long sectionId : sectionIds) {
            Section section = sectionRepository.findById(sectionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Section not found with id: " + sectionId));

            // Regla: la seccion debe pertenecer al periodo que se esta inscribiendo
            if (!section.getAcademicPeriod().getId().equals(period.getId())) {
                throw new BusinessException(
                        "Section " + sectionId + " does not belong to academic period '" + period.getCode() + "'"
                );
            }

            Subject subject = section.getSubject();

            // Regla: solo asignaturas del plan de estudios del estudiante
            if (!subject.getStudyPlan().getId().equals(student.getStudyPlan().getId())) {
                throw new BusinessException(
                        "Subject '" + subject.getCode() + "' does not belong to the student's study plan"
                );
            }

            // Regla: una asignatura aprobada no se vuelve a inscribir
            if (approvedCodes.contains(subject.getCode())) {
                throw new BusinessException(
                        "Subject '" + subject.getCode() + "' is already APROBADO and cannot be enrolled again"
                );
            }

            // Regla: la misma asignatura no se inscribe dos veces en el periodo
            if (enrolledSubjectIds.contains(subject.getId())) {
                throw new BusinessException(
                        "Subject '" + subject.getCode() + "' is already enrolled in academic period '"
                                + period.getCode() + "'"
                );
            }

            // Regla: los prerrequisitos deben estar aprobados
            for (Prerequisite prerequisite : prerequisiteRepository.findBySubjectId(subject.getId())) {
                String requiredCode = prerequisite.getPrerequisiteSubject().getCode();
                if (!approvedCodes.contains(requiredCode)) {
                    throw new BusinessException(
                            "Subject '" + subject.getCode() + "' requires prerequisite '" + requiredCode
                                    + "', which is not APROBADO"
                    );
                }
            }

            selected.add(section);
        }

        return selected;
    }

    /**
     * Regla: no se inscriben asignaturas de un semestre superior mientras queden
     * pendientes disponibles de semestres anteriores sin seleccionar.
     */
    private void requireLowerSemestersFirst(List<Section> selected, List<Section> candidates) {
        Set<Long> selectedSubjectIds = selected.stream()
                .map(section -> section.getSubject().getId())
                .collect(Collectors.toSet());

        for (Section section : selected) {
            int semester = section.getSubject().getSemester();

            Optional<Section> pending = candidates.stream()
                    .filter(candidate -> candidate.getSubject().getSemester() < semester)
                    .filter(candidate -> !selectedSubjectIds.contains(candidate.getSubject().getId()))
                    .findFirst();

            if (pending.isPresent()) {
                Subject pendingSubject = pending.get().getSubject();
                throw new BusinessException(
                        "Subject '" + section.getSubject().getCode() + "' (semester " + semester
                                + ") cannot be enrolled while '" + pendingSubject.getCode() + "' (semester "
                                + pendingSubject.getSemester() + ") is still available"
                );
            }
        }
    }

    /** Regla: la seccion debe tener cupos disponibles. */
    private void requireAvailableSeats(List<Section> selected) {
        for (Section section : selected) {
            long taken = enrollmentRepository.countBySectionIdAndStatus(section.getId(), EnrollmentStatus.INSCRITA);
            if (taken >= section.getCapacity()) {
                throw new BusinessException(
                        "Section of subject '" + section.getSubject().getCode()
                                + "' has no available seats (capacity " + section.getCapacity() + ")"
                );
            }
        }
    }

    /** Regla: las secciones inscritas no pueden coincidir en dia y modulo. */
    private void requireNoScheduleConflict(List<Enrollment> currentEnrollments, List<Section> selected) {
        Map<String, String> takenBlocks = new HashMap<>();

        for (Enrollment enrollment : currentEnrollments) {
            addBlocks(takenBlocks, enrollment.getSection());
        }
        for (Section section : selected) {
            addBlocks(takenBlocks, section);
        }
    }

    private void addBlocks(Map<String, String> takenBlocks, Section section) {
        for (ScheduleBlock block : section.getScheduleBlocks()) {
            String key = block.getDay() + "-" + block.getModule();
            String owner = takenBlocks.get(key);
            if (owner != null) {
                throw new BusinessException(
                        "Schedule conflict on " + block.getDay() + " module " + block.getModule()
                                + " between '" + owner + "' and '" + section.getSubject().getCode() + "'"
                );
            }
            takenBlocks.put(key, section.getSubject().getCode());
        }
    }

    /**
     * Regla: entre 3 y 6 asignaturas por periodo. Si al estudiante le quedan menos
     * de 3 disponibles, puede inscribir solo las pendientes.
     */
    private void requireSubjectCountInRange(int alreadyEnrolled, int newlySelected, int availableCandidates) {
        int total = alreadyEnrolled + newlySelected;

        if (total > MAX_SUBJECTS) {
            throw new BusinessException(
                    "A student cannot enroll more than " + MAX_SUBJECTS
                            + " subjects per academic period (" + total + " given)"
            );
        }

        int minimum = Math.min(MIN_SUBJECTS, alreadyEnrolled + availableCandidates);
        if (total < minimum) {
            throw new BusinessException(
                    "A student must enroll at least " + minimum
                            + " subjects in this academic period (" + total + " given)"
            );
        }
    }

    private boolean prerequisitesApproved(Subject subject, Set<String> approvedCodes) {
        return prerequisiteRepository.findBySubjectId(subject.getId()).stream()
                .allMatch(prerequisite -> approvedCodes.contains(prerequisite.getPrerequisiteSubject().getCode()));
    }

    /** El historial guarda el codigo de la asignatura como snapshot. */
    private Set<String> approvedSubjectCodes(Long studentId) {
        return academicHistoryRepository
                .findByStudentIdAndResult(studentId, AcademicHistoryResult.APROBADO).stream()
                .map(AcademicHistory::getSubjectCode)
                .collect(Collectors.toSet());
    }

    private Student findStudentOrThrow(Long studentId) {
        return studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with id: " + studentId));
    }

    private AcademicPeriod findPeriodOrThrow(Long periodId) {
        return academicPeriodRepository.findById(periodId)
                .orElseThrow(() -> new ResourceNotFoundException("Academic period not found with id: " + periodId));
    }

    private void requireOpenPeriod(AcademicPeriod period) {
        if (period.getStatus() != AcademicPeriodStatus.ABIERTO) {
            throw new BusinessException(
                    "Academic period '" + period.getCode() + "' is CERRADO: enrollments cannot be changed"
            );
        }
    }
}
