package com.example.siga.dto;

import com.example.siga.entity.MatriculationStatus;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentPeriodStatusResponseDTO {
    private Long id;

    private Long studentId;
    private String studentRun;
    private String studentFullName;

    private Long academicPeriodId;
    private String academicPeriodCode;

    private MatriculationStatus enrollmentStatus;
}
