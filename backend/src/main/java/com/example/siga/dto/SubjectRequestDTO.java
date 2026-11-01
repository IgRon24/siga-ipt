package com.example.siga.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubjectRequestDTO {

    @NotBlank(message = "Code is required")
    @Size(max = 20, message = "Code must not exceed 20 characters")
    private String code;

    @NotBlank(message = "Name is required")
    @Size(max = 150, message = "Name must not exceed 150 characters")
    private String name;

    @NotNull(message = "Semester is required")
    @Min(value = 1, message = "Semester must be between 1 and 4")
    @Max(value = 4, message = "Semester must be between 1 and 4")
    private Integer semester;

    @NotNull(message = "Theory hours are required")
    @Min(value = 0, message = "Theory hours cannot be negative")
    private Integer telTheory;

    @NotNull(message = "Exercise hours are required")
    @Min(value = 0, message = "Exercise hours cannot be negative")
    private Integer telExercises;

    @NotNull(message = "Lab hours are required")
    @Min(value = 0, message = "Lab hours cannot be negative")
    private Integer telLab;

    @NotNull(message = "SCT is required")
    @Min(value = 1, message = "SCT must be greater than 0")
    @Max(value = 7, message = "SCT must not exceed 7")
    private Integer sct;

    @NotNull(message = "Study plan id is required")
    private Long studyPlanId;

    @AssertTrue(message = "Total TEL hours (theory + exercises + lab) must be less than 8")
    public boolean isTelValid() {
        if (telTheory == null || telExercises == null || telLab == null) {
            return true; // los @NotNull reportan el nulo; evitamos NPE aquí
        }
        return (telTheory + telExercises + telLab) < 8;
    }
}
