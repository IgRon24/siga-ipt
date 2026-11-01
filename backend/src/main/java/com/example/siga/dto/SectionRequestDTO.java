package com.example.siga.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SectionRequestDTO {

    @NotNull(message = "Subject id is required")
    private Long subjectId;

    @NotNull(message = "Academic period id is required")
    private Long academicPeriodId;

    @NotNull(message = "Teacher id is required")
    private Long teacherId;

    @NotNull(message = "Capacity is required")
    @Min(value = 1, message = "Capacity must be greater than 0")
    @Max(value = 60, message = "Capacity must not exceed 60")
    private Integer capacity;

    /**
     * Horario completo de la seccion. La cantidad exacta de bloques la valida el
     * servicio contra las horas TEL de la asignatura (cada bloque son 2 horas).
     */
    @NotEmpty(message = "At least one schedule block is required")
    @Valid
    private List<ScheduleBlockRequestDTO> scheduleBlocks;
}
