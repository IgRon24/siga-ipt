package com.example.siga.repository;

import com.example.siga.entity.Career;
import com.example.siga.entity.CareerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CareerRepository extends JpaRepository<Career, Long> {

    boolean existsByCode(String code);

    Optional<Career> findByCode(String code);

    List<Career> findBystatus(CareerStatus status);

}
