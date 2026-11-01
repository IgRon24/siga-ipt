package com.example.siga.repository;

import com.example.siga.entity.AcademicHistory;
import com.example.siga.entity.AcademicHistoryResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AcademicHistoryRepository extends JpaRepository<AcademicHistory, Long> {

    List<AcademicHistory> findByStudentId(Long studentId);

    /** Asignaturas ya cursadas con un resultado determinado (snapshot por codigo). */
    List<AcademicHistory> findByStudentIdAndResult(Long studentId, AcademicHistoryResult result);

    long countByStudentIdAndSubjectCodeAndResult(Long studentId, String subjectCode, AcademicHistoryResult result);

    /** Usado para impedir borrar un estudiante con historial academico. */
    boolean existsByStudentId(Long studentId);

    /** Usado para impedir borrar una asignatura que aparece en el historial. */
    boolean existsBySubjectCode(String subjectCode);
}
