package com.example.siga.repository;

import com.example.siga.entity.Grade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GradeRepository extends JpaRepository<Grade, Long> {

    Optional<Grade> findByEnrollmentId(Long enrollmentId);

    /** Regla: una sola calificacion final por inscripcion. */
    boolean existsByEnrollmentId(Long enrollmentId);

    /** Acta de la seccion: las notas ya registradas de sus inscripciones. */
    List<Grade> findByEnrollmentSectionId(Long sectionId);

    /** Usado por el cierre de periodo para detectar las inscripciones sin nota. */
    List<Grade> findByEnrollmentAcademicPeriodId(Long academicPeriodId);
}
