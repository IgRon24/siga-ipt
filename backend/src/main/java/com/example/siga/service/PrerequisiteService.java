package com.example.siga.service;

import com.example.siga.dto.PrerequisiteRequestDTO;
import com.example.siga.dto.PrerequisiteResponseDTO;

import java.util.List;

public interface PrerequisiteService {

    List<PrerequisiteResponseDTO> findBySubject(Long subjectId);
    PrerequisiteResponseDTO add(Long subjectId, PrerequisiteRequestDTO dto);
    void remove(Long subjectId, Long prerequisiteSubjectId);
}
