package com.example.siga.service;

import com.example.siga.dto.GradeRequestDTO;
import com.example.siga.dto.GradeResponseDTO;

import java.util.List;

public interface GradeService {

    /**
     * Acta de la seccion: una fila por inscripcion INSCRITA, con nota o sin ella.
     * Un teacherId nulo significa ADMIN y omite la validacion de docente responsable.
     */
    List<GradeResponseDTO> findBySection(Long teacherId, Long sectionId);

    GradeResponseDTO create(Long teacherId, GradeRequestDTO dto);

    GradeResponseDTO update(Long teacherId, Long id, GradeRequestDTO dto);
}
