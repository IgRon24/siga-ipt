package com.example.siga.service;

import com.example.siga.dto.AcademicHistoryResponseDTO;

import java.util.List;

public interface AcademicHistoryService {

    /** Historial academico del estudiante, ordenado por periodo y asignatura. */
    List<AcademicHistoryResponseDTO> findByStudent(Long studentId);
}
