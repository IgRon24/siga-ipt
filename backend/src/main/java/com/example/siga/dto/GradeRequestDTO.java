package com.example.siga.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

/**
 * La calificacion siempre se identifica por la inscripcion: el estudiante, la
 * asignatura y el periodo salen de ella y nunca se envian por separado.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GradeRequestDTO {

    @NotNull(message = "Enrollment id is required")
    private Long enrollmentId;

    @NotNull(message = "Grade value is required")
    @DecimalMin(value = "1.0", message = "Grade value must be at least 1.0")
    @DecimalMax(value = "7.0", message = "Grade value must be at most 7.0")
    private BigDecimal value;
}
