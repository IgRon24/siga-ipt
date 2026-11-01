package com.example.siga.repository;

import com.example.siga.entity.Career;
import com.example.siga.entity.StudyPlan;
import com.example.siga.entity.StudyPlanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudyPlanRepository extends JpaRepository<StudyPlan, Long>{

    List<StudyPlan> findByCareerId(Long careerId);

    Optional<StudyPlan> findByCareerIdAndStatus(Long careerId, StudyPlanStatus status);

    boolean existsByCareerId(Long careerId);

    boolean existsByCareerIdAndIdentifier(Long careerId, String identifier);
}
