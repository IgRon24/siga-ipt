package com.example.siga.repository;

import com.example.siga.entity.AcademicPeriod;
import com.example.siga.entity.AcademicPeriodStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AcademicPeriodRepository extends JpaRepository<AcademicPeriod, Long> {

    boolean existsByYearAndSemester(Integer year, Integer semester);

    Optional<AcademicPeriod> findByCode(String code);

    List<AcademicPeriod> findByStatus(AcademicPeriodStatus status);
}
