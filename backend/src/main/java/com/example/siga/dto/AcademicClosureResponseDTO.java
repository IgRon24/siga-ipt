package com.example.siga.dto;

import com.example.siga.entity.AcademicPeriodStatus;
import lombok.*;

import java.util.List;

/** Resumen de lo consolidado al cerrar un periodo academico. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AcademicClosureResponseDTO {

    private Long academicPeriodId;
    private String academicPeriodCode;
    private AcademicPeriodStatus status;

    /** Inscripciones INSCRITA que pasaron al historial academico. */
    private int consolidatedEnrollments;
    private int approvedCount;
    private int failedCount;

    /** Estudiantes que quedaron EGRESADO por aprobar todo su plan. */
    private List<String> graduatedStudentRuns;

    /** Estudiantes que quedaron ELIMINADO por reprobar dos veces la misma asignatura. */
    private List<String> eliminatedStudentRuns;
}
