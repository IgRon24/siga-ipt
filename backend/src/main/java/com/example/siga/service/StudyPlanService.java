package com.example.siga.service;

import com.example.siga.dto.StudyPlanRequestDTO;
import com.example.siga.dto.StudyPlanResponseDTO;

import java.util.List;

public interface StudyPlanService {

    StudyPlanResponseDTO create(StudyPlanRequestDTO dto);

    StudyPlanResponseDTO findById(Long id);

    List<StudyPlanResponseDTO> findAll();

    List<StudyPlanResponseDTO> findByCareer(Long careerId);

    StudyPlanResponseDTO update(Long id, StudyPlanRequestDTO dto);

    StudyPlanResponseDTO changeStatus(Long id, String newStatus);

    void delete(Long id);
}
