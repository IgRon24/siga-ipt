package com.example.siga.dto;

import com.example.siga.entity.StudentStatus;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentResponseDTO {
    private Long id;
    private String run;
    private String fullName;
    private String email;
    private StudentStatus status;
    private Long careerId;
    private String careerCode;
    private String careerName;
    private Long studyPlanId;
    private String studyPlanIdentifier;
}
