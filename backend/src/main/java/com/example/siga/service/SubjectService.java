package com.example.siga.service;

import com.example.siga.dto.SubjectRequestDTO;
import com.example.siga.dto.SubjectResponseDTO;

import java.util.List;

public interface SubjectService {

    SubjectResponseDTO create(SubjectRequestDTO dto);
    SubjectResponseDTO findById(Long id);
    List<SubjectResponseDTO> findAll();
    List<SubjectResponseDTO> findByStudyPlan(Long studyPlanId);
    SubjectResponseDTO update(Long id, SubjectRequestDTO dto);
    void delete(Long id);
}
