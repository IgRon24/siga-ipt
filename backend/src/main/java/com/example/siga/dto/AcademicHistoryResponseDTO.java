package com.example.siga.dto;

import com.example.siga.entity.AcademicHistoryResult;
import lombok.*;

import java.math.BigDecimal;

/**
 * Fila del historial academico. El codigo y el nombre de la asignatura son el
 * snapshot guardado al cierre, no los actuales de la asignatura.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AcademicHistoryResponseDTO {
    private Long id;

    private Long studentId;
    private String studentRun;
    private String studentFullName;

    private String subjectCode;
    private String subjectName;

    private Long academicPeriodId;
    private String academicPeriodCode;

    private BigDecimal grade;
    private AcademicHistoryResult result;
}
