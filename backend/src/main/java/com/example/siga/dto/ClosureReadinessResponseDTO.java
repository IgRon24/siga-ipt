package com.example.siga.dto;

import com.example.siga.entity.AcademicPeriodStatus;
import lombok.*;

import java.util.List;

/**
 * Diagnostico previo al cierre de un periodo: responde si se puede cerrar y,
 * si no, por que. Evita que el cliente tenga que pedir el acta de cada seccion
 * para contar las notas pendientes.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClosureReadinessResponseDTO {

    private Long academicPeriodId;
    private String academicPeriodCode;
    private AcademicPeriodStatus status;

    /** Verdadero solo si el periodo esta ABIERTO y no queda ninguna nota pendiente. */
    private boolean canClose;

    private int sectionCount;

    /** Inscripciones INSCRITA del periodo: las que el cierre consolidaria. */
    private int enrolledCount;
    private int gradedCount;
    private int pendingCount;

    /** Las mismas asignaturas que listaria el 409 del cierre, sin repetir. */
    private List<String> pendingSubjectCodes;

    private List<SectionReadinessResponseDTO> sections;
}
