package com.example.siga.dto;

import lombok.*;

/**
 * Estado de una seccion frente al cierre del periodo. Lo que decide el cierre
 * es {@code pending}: mientras exista una inscripcion INSCRITA sin nota, el
 * cierre se rechaza con 409.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SectionReadinessResponseDTO {

    private SectionResponseDTO section;

    /** Inscripciones INSCRITA de la seccion: las ANULADA no se califican. */
    private int enrolled;
    private int graded;
    private int pending;
}
