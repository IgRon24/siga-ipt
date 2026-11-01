package com.example.siga.repository;

import com.example.siga.entity.Enrollment;
import com.example.siga.entity.EnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    List<Enrollment> findByStudentId(Long studentId);

    List<Enrollment> findByStudentIdAndAcademicPeriodIdAndStatus(
            Long studentId, Long academicPeriodId, EnrollmentStatus status);

    Optional<Enrollment> findByIdAndStudentId(Long id, Long studentId);

    /** Acta de notas de una seccion: las anuladas no se califican. */
    List<Enrollment> findBySectionIdAndStatus(Long sectionId, EnrollmentStatus status);

    /** Usado por el cierre de periodo: todo lo inscrito debe quedar en el historial. */
    List<Enrollment> findByAcademicPeriodIdAndStatus(Long academicPeriodId, EnrollmentStatus status);

    /** Cupos ocupados de una seccion: las anuladas no cuentan. */
    long countBySectionIdAndStatus(Long sectionId, EnrollmentStatus status);

    /** Usado para impedir borrar una seccion con inscritos. */
    boolean existsBySectionIdAndStatus(Long sectionId, EnrollmentStatus status);

    /** Cualquier inscripcion, incluso ANULADA, mantiene la referencia a la seccion. */
    boolean existsBySectionId(Long sectionId);

    /** Usado para impedir borrar un estudiante con inscripciones. */
    boolean existsByStudentId(Long studentId);

    /** Usado para impedir borrar una asignatura con inscripciones. */
    boolean existsBySubjectId(Long subjectId);
}
