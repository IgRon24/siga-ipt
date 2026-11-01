package com.example.siga.repository;

import com.example.siga.entity.MatriculationStatus;
import com.example.siga.entity.StudentPeriodStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentPeriodStatusRepository extends JpaRepository<StudentPeriodStatus, Long> {

    Optional<StudentPeriodStatus> findByStudentIdAndAcademicPeriodId(Long studentId, Long academicPeriodId);

    boolean existsByStudentIdAndAcademicPeriodIdAndEnrollmentStatus(
            Long studentId, Long academicPeriodId, MatriculationStatus enrollmentStatus);

    List<StudentPeriodStatus> findByAcademicPeriodId(Long academicPeriodId);

    /** Usado para impedir borrar un periodo academico con matriculas registradas. */
    boolean existsByAcademicPeriodId(Long academicPeriodId);

    List<StudentPeriodStatus> findByStudentId(Long studentId);

    /** La matricula no es informacion academica: se borra junto con el estudiante. */
    void deleteByStudentId(Long studentId);
}
