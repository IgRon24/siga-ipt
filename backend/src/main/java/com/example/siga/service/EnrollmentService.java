package com.example.siga.service;

import com.example.siga.dto.AvailableSectionResponseDTO;
import com.example.siga.dto.EnrollmentRequestDTO;
import com.example.siga.dto.EnrollmentResponseDTO;

import java.util.List;

public interface EnrollmentService {

    /** Inscribe el conjunto completo de asignaturas seleccionadas por el estudiante. */
    List<EnrollmentResponseDTO> enroll(Long studentId, EnrollmentRequestDTO dto);

    List<AvailableSectionResponseDTO> findAvailable(Long studentId, Long academicPeriodId);

    List<EnrollmentResponseDTO> findByStudent(Long studentId);

    /** Anulacion: la inscripcion pasa a ANULADA y libera el cupo. */
    void cancel(Long studentId, Long enrollmentId);
}
