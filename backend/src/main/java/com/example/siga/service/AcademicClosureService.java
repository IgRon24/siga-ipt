package com.example.siga.service;

import com.example.siga.dto.AcademicClosureResponseDTO;
import com.example.siga.dto.ClosureReadinessResponseDTO;

public interface AcademicClosureService {

    /**
     * Cierra el periodo: exige todas las notas registradas, consolida el historial
     * academico y actualiza el estado de los estudiantes. Es la unica via para
     * dejar un periodo en CERRADO.
     */
    AcademicClosureResponseDTO close(Long academicPeriodId);

    /**
     * Diagnostico previo: cuantas notas faltan y en que asignaturas, es decir
     * si close() aceptaria o rechazaria el cierre. No modifica nada.
     */
    ClosureReadinessResponseDTO readiness(Long academicPeriodId);
}
