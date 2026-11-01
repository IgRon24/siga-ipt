package com.example.siga.repository;

import com.example.siga.entity.AcademicPeriodStatus;
import com.example.siga.entity.Section;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SectionRepository extends JpaRepository<Section, Long> {

    boolean existsBySubjectIdAndAcademicPeriodId(Long subjectId, Long academicPeriodId);

    List<Section> findByAcademicPeriodId(Long academicPeriodId);

    List<Section> findByTeacherId(Long teacherId);

    /** Usado para detectar choques de horario del docente dentro de un periodo. */
    List<Section> findByTeacherIdAndAcademicPeriodId(Long teacherId, Long academicPeriodId);

    /** Usado para impedir borrar un docente con secciones. */
    boolean existsByTeacherId(Long teacherId);

    /** Usado para impedir inactivar un docente con secciones en un periodo ABIERTO. */
    boolean existsByTeacherIdAndAcademicPeriodStatus(Long teacherId, AcademicPeriodStatus status);

    /** Usado para impedir borrar una asignatura con secciones. */
    boolean existsBySubjectId(Long subjectId);

    /** Usado para impedir borrar un periodo academico con secciones. */
    boolean existsByAcademicPeriodId(Long academicPeriodId);
}
