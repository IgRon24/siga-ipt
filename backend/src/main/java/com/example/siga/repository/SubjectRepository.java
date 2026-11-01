package com.example.siga.repository;

import com.example.siga.entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, Long> {
    List<Subject> findByStudyPlanId(Long studyPlanId);

    boolean existsByStudyPlanIdAndCode(Long studyPlanId, String code);
}
