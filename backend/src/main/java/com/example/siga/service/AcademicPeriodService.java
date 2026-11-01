package com.example.siga.service;

import com.example.siga.dto.AcademicPeriodRequestDTO;
import com.example.siga.dto.AcademicPeriodResponseDTO;

import java.util.List;

public interface AcademicPeriodService {

    AcademicPeriodResponseDTO create(AcademicPeriodRequestDTO dto);
    AcademicPeriodResponseDTO findById(Long id);
    List<AcademicPeriodResponseDTO> findAll();
    List<AcademicPeriodResponseDTO> findByStatus(String status);
    AcademicPeriodResponseDTO update(Long id, AcademicPeriodRequestDTO dto);
    AcademicPeriodResponseDTO changeStatus(Long id, String newStatus);
    void delete(Long id);
}
