package com.example.siga.repository;

import com.example.siga.entity.Student;
import com.example.siga.entity.StudentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {

    boolean existsByRun(String run);

    boolean existsByEmail(String email);

    Optional<Student> findByRun(String run);

    Optional<Student> findByEmail(String email);

    List<Student> findByStatus(StudentStatus status);

    List<Student> findByCareerId(Long careerId);

    boolean existsByCareerId(Long careerId);

    boolean existsByStudyPlanId(Long studyPlanId);
}
