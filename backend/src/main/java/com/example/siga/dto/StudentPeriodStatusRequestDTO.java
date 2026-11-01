package com.example.siga.dto;

import com.example.siga.entity.MatriculationStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentPeriodStatusRequestDTO {

    @NotNull(message = "Student id is required")
    private Long studentId;

    @NotNull(message = "Academic period id is required")
    private Long academicPeriodId;

    @NotNull(message = "Enrollment status is required (MATRICULATED or NOT_MATRICULATED)")
    private MatriculationStatus enrollmentStatus;
}
