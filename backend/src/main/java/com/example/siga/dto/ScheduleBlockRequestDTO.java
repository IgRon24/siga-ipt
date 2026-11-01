package com.example.siga.dto;

import com.example.siga.entity.ScheduleDay;
import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleBlockRequestDTO {

    @NotNull(message = "Day is required (L, M, W, J or V)")
    private ScheduleDay day;

    @NotNull(message = "Module is required")
    @Min(value = 1, message = "Module must be between 1 and 6")
    @Max(value = 6, message = "Module must be between 1 and 6")
    private Integer module;
}
