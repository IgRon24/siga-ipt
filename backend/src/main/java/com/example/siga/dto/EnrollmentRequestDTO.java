package com.example.siga.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

/**
 * La inscripcion se envia completa: el minimo de 3 y el maximo de 6 asignaturas
 * solo se pueden validar sobre el conjunto, no sobre una asignatura a la vez.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnrollmentRequestDTO {

    @NotNull(message = "Academic period id is required")
    private Long academicPeriodId;

    @NotEmpty(message = "At least one section is required")
    private List<Long> sectionIds;
}
