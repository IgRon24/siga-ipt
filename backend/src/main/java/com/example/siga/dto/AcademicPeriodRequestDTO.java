package com.example.siga.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AcademicPeriodRequestDTO {

    @NotNull(message = "Year is required")
    @Min(value = 2000, message = "Year must be between 2000 and 2100")
    @Max(value = 2100, message = "Year must be between 2000 and 2100")
    private Integer year;

    @NotNull(message = "Semester is required")
    @Min(value = 1, message = "Semester must be 1 or 2")
    @Max(value = 2, message = "Semester must be 1 or 2")
    private Integer semester;

    // El codigo lo arma el servicio ("2026-1") y el estado tampoco se recibe:
    // todo periodo nace ABIERTO y se cierra con PATCH /api/academic-periods/{id}/status
}
