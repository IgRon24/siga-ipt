package com.example.siga.repository;

import com.example.siga.entity.Teacher;
import com.example.siga.entity.TeacherStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeacherRepository extends JpaRepository<Teacher, Long> {

    boolean existsByRun(String run);

    boolean existsByEmail(String email);

    Optional<Teacher> findByRun(String run);

    /** Usado para identificar al docente responsable a partir del email del token. */
    Optional<Teacher> findByEmail(String email);

    List<Teacher> findByStatus(TeacherStatus status);
}
