package com.example.siga.service.impl;

import com.example.siga.dto.AcademicClosureResponseDTO;
import com.example.siga.dto.ClosureReadinessResponseDTO;
import com.example.siga.dto.SectionReadinessResponseDTO;
import com.example.siga.entity.AcademicHistory;
import com.example.siga.entity.AcademicHistoryResult;
import com.example.siga.entity.AcademicPeriod;
import com.example.siga.entity.AcademicPeriodStatus;
import com.example.siga.entity.Enrollment;
import com.example.siga.entity.EnrollmentStatus;
import com.example.siga.entity.Grade;
import com.example.siga.entity.Section;
import com.example.siga.entity.Student;
import com.example.siga.entity.StudentStatus;
import com.example.siga.entity.Subject;
import com.example.siga.exception.BusinessException;
import com.example.siga.exception.ResourceNotFoundException;
import com.example.siga.mapper.SectionMapper;
import com.example.siga.repository.AcademicHistoryRepository;
import com.example.siga.repository.AcademicPeriodRepository;
import com.example.siga.repository.EnrollmentRepository;
import com.example.siga.repository.GradeRepository;
import com.example.siga.repository.SectionRepository;
import com.example.siga.repository.StudentRepository;
import com.example.siga.repository.SubjectRepository;
import com.example.siga.service.AcademicClosureService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AcademicClosureServiceImpl implements AcademicClosureService {

    /** Regla: reprobar dos veces la misma asignatura elimina al estudiante. */
    private static final long MAX_FAILURES_PER_SUBJECT = 2;

    private final AcademicPeriodRepository academicPeriodRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final GradeRepository gradeRepository;
    private final AcademicHistoryRepository academicHistoryRepository;
    private final StudentRepository studentRepository;
    private final SubjectRepository subjectRepository;
    private final SectionRepository sectionRepository;
    private final SectionMapper sectionMapper;

    @Override
    @Transactional
    public AcademicClosureResponseDTO close(Long academicPeriodId) {
        AcademicPeriod period = findPeriodOrThrow(academicPeriodId);

        // Regla: el cierre solo se ejecuta sobre un periodo ABIERTO y no se repite
        if (period.getStatus() != AcademicPeriodStatus.ABIERTO) {
            throw new BusinessException(
                    "Academic period '" + period.getCode() + "' is already CERRADO and cannot be closed again"
            );
        }

        // Las anuladas no dejan historial: solo se consolidan las INSCRITA
        List<Enrollment> enrollments = enrollmentRepository
                .findByAcademicPeriodIdAndStatus(academicPeriodId, EnrollmentStatus.INSCRITA);

        Map<Long, Grade> gradesByEnrollment = gradeRepository
                .findByEnrollmentAcademicPeriodId(academicPeriodId).stream()
                .collect(Collectors.toMap(grade -> grade.getEnrollment().getId(), Function.identity()));

        requireEveryEnrollmentGraded(period, enrollments, gradesByEnrollment);

        // Las decisiones se calculan sobre el historial previo mas los resultados del
        // periodo, antes de insertar nada, para no depender del orden de escritura.
        Map<Long, List<AcademicHistory>> historyByStudent = buildHistory(enrollments, gradesByEnrollment);

        List<String> graduated = new ArrayList<>();
        List<String> eliminated = new ArrayList<>();

        historyByStudent.forEach((studentId, records) -> {
            Student student = records.get(0).getStudent();

            // EGRESADO y ELIMINADO son terminales: un estudiante ya cerrado no se reevalua
            if (student.getStatus() != StudentStatus.REGULAR) {
                return;
            }

            if (failedTwice(studentId, records)) {
                student.setStatus(StudentStatus.ELIMINADO);
                studentRepository.save(student);
                eliminated.add(student.getRun());
            } else if (approvedWholePlan(student, records)) {
                student.setStatus(StudentStatus.EGRESADO);
                studentRepository.save(student);
                graduated.add(student.getRun());
            }
        });

        List<AcademicHistory> allRecords = historyByStudent.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
        academicHistoryRepository.saveAll(allRecords);

        period.setStatus(AcademicPeriodStatus.CERRADO);
        academicPeriodRepository.save(period);

        return AcademicClosureResponseDTO.builder()
                .academicPeriodId(period.getId())
                .academicPeriodCode(period.getCode())
                .status(period.getStatus())
                .consolidatedEnrollments(allRecords.size())
                .approvedCount(countResult(allRecords, AcademicHistoryResult.APROBADO))
                .failedCount(countResult(allRecords, AcademicHistoryResult.REPROBADO))
                .graduatedStudentRuns(graduated)
                .eliminatedStudentRuns(eliminated)
                .build();
    }

    /**
     * Diagnostico del cierre, sin efectos: deja ver lo que decidiria close()
     * antes de ejecutarlo. Son tres consultas (secciones, inscripciones y notas
     * del periodo) en lugar del acta de cada seccion una por una.
     */
    @Override
    @Transactional(readOnly = true)
    public ClosureReadinessResponseDTO readiness(Long academicPeriodId) {
        AcademicPeriod period = findPeriodOrThrow(academicPeriodId);

        List<Enrollment> enrollments = enrollmentRepository
                .findByAcademicPeriodIdAndStatus(academicPeriodId, EnrollmentStatus.INSCRITA);

        Set<Long> gradedEnrollmentIds = gradeRepository
                .findByEnrollmentAcademicPeriodId(academicPeriodId).stream()
                .map(grade -> grade.getEnrollment().getId())
                .collect(Collectors.toSet());

        Map<Long, Integer> enrolledBySection = new HashMap<>();
        Map<Long, Integer> gradedBySection = new HashMap<>();
        for (Enrollment enrollment : enrollments) {
            Long sectionId = enrollment.getSection().getId();
            enrolledBySection.merge(sectionId, 1, Integer::sum);
            if (gradedEnrollmentIds.contains(enrollment.getId())) {
                gradedBySection.merge(sectionId, 1, Integer::sum);
            }
        }

        List<SectionReadinessResponseDTO> sections = new ArrayList<>();
        for (Section section : sectionRepository.findByAcademicPeriodId(academicPeriodId)) {
            int enrolled = enrolledBySection.getOrDefault(section.getId(), 0);
            int graded = gradedBySection.getOrDefault(section.getId(), 0);
            sections.add(SectionReadinessResponseDTO.builder()
                    .section(sectionMapper.toResponseDTO(section))
                    .enrolled(enrolled)
                    .graded(graded)
                    .pending(enrolled - graded)
                    .build());
        }

        // La misma lista que daria el 409, calculada por el mismo metodo
        List<String> pendingSubjectCodes = pendingSubjectCodes(enrollments, gradedEnrollmentIds);
        int gradedCount = (int) enrollments.stream()
                .filter(enrollment -> gradedEnrollmentIds.contains(enrollment.getId()))
                .count();
        int pendingCount = enrollments.size() - gradedCount;

        return ClosureReadinessResponseDTO.builder()
                .academicPeriodId(period.getId())
                .academicPeriodCode(period.getCode())
                .status(period.getStatus())
                .canClose(period.getStatus() == AcademicPeriodStatus.ABIERTO && pendingCount == 0)
                .sectionCount(sections.size())
                .enrolledCount(enrollments.size())
                .gradedCount(gradedCount)
                .pendingCount(pendingCount)
                .pendingSubjectCodes(pendingSubjectCodes)
                .sections(sections)
                .build();
    }

    private AcademicPeriod findPeriodOrThrow(Long academicPeriodId) {
        return academicPeriodRepository.findById(academicPeriodId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Academic period not found with id: " + academicPeriodId));
    }

    /**
     * Regla: no se cierra con calificaciones pendientes. El error informa las
     * asignaturas de las secciones incompletas para que el docente las registre.
     */
    private void requireEveryEnrollmentGraded(AcademicPeriod period,
                                              List<Enrollment> enrollments,
                                              Map<Long, Grade> gradesByEnrollment) {
        List<String> pendingSubjects = pendingSubjectCodes(enrollments, gradesByEnrollment.keySet());

        if (!pendingSubjects.isEmpty()) {
            throw new BusinessException(
                    "Academic period '" + period.getCode()
                            + "' cannot be closed: pending grades in sections of subjects "
                            + pendingSubjects
            );
        }
    }

    /**
     * Las asignaturas con inscripciones sin nota, sin repetir y en el orden de
     * las inscripciones. Lo comparten el rechazo del cierre y el diagnostico,
     * para que ambos nombren exactamente las mismas asignaturas.
     */
    private List<String> pendingSubjectCodes(List<Enrollment> enrollments, Set<Long> gradedEnrollmentIds) {
        return enrollments.stream()
                .filter(enrollment -> !gradedEnrollmentIds.contains(enrollment.getId()))
                .map(enrollment -> enrollment.getSubject().getCode())
                .distinct()
                .collect(Collectors.toList());
    }

    /** Snapshot inmutable: el codigo y el nombre de la asignatura se copian ahora. */
    private Map<Long, List<AcademicHistory>> buildHistory(List<Enrollment> enrollments,
                                                          Map<Long, Grade> gradesByEnrollment) {
        Map<Long, List<AcademicHistory>> historyByStudent = new LinkedHashMap<>();

        for (Enrollment enrollment : enrollments) {
            Grade grade = gradesByEnrollment.get(enrollment.getId());
            Subject subject = enrollment.getSubject();

            AcademicHistory record = AcademicHistory.builder()
                    .student(enrollment.getStudent())
                    .subjectCode(subject.getCode())
                    .subjectName(subject.getName())
                    .academicPeriod(enrollment.getAcademicPeriod())
                    .grade(grade.getValue())
                    .result(grade.getResult())
                    .build();

            historyByStudent
                    .computeIfAbsent(enrollment.getStudent().getId(), id -> new ArrayList<>())
                    .add(record);
        }

        return historyByStudent;
    }

    /** Las reprobaciones previas del historial mas la de este periodo. */
    private boolean failedTwice(Long studentId, List<AcademicHistory> records) {
        return records.stream()
                .filter(record -> record.getResult() == AcademicHistoryResult.REPROBADO)
                .anyMatch(record -> academicHistoryRepository.countByStudentIdAndSubjectCodeAndResult(
                        studentId, record.getSubjectCode(), AcademicHistoryResult.REPROBADO)
                        + 1 >= MAX_FAILURES_PER_SUBJECT);
    }

    /** Regla: aprobar todas las asignaturas del plan deja al estudiante EGRESADO. */
    private boolean approvedWholePlan(Student student, List<AcademicHistory> records) {
        Set<String> approvedCodes = academicHistoryRepository
                .findByStudentIdAndResult(student.getId(), AcademicHistoryResult.APROBADO).stream()
                .map(AcademicHistory::getSubjectCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        records.stream()
                .filter(record -> record.getResult() == AcademicHistoryResult.APROBADO)
                .forEach(record -> approvedCodes.add(record.getSubjectCode()));

        List<Subject> planSubjects = subjectRepository.findByStudyPlanId(student.getStudyPlan().getId());

        return !planSubjects.isEmpty() && planSubjects.stream()
                .allMatch(subject -> approvedCodes.contains(subject.getCode()));
    }

    private int countResult(List<AcademicHistory> records, AcademicHistoryResult result) {
        return (int) records.stream()
                .filter(record -> record.getResult() == result)
                .count();
    }
}
