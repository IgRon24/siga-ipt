package com.example.siga.repository;

import com.example.siga.entity.Prerequisite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PrerequisiteRepository extends JpaRepository<Prerequisite, Long> {

    List<Prerequisite> findBySubjectId(Long subjectId);

    Optional<Prerequisite> findBySubjectIdAndPrerequisiteSubjectId(Long subjectId, Long prerequisiteSubjectId);

    boolean existsBySubjectIdAndPrerequisiteSubjectId(Long subjectId, Long prerequisiteSubjectId);

    long countBySubjectId(Long subjectId);

    /** Usado para impedir borrar una asignatura que es prerrequisito de otra. */
    boolean existsByPrerequisiteSubjectId(Long prerequisiteSubjectId);

    /** Usado para revalidar los semestres cuando una asignatura cambia de semestre. */
    List<Prerequisite> findByPrerequisiteSubjectId(Long prerequisiteSubjectId);

    void deleteBySubjectId(Long subjectId);
}
