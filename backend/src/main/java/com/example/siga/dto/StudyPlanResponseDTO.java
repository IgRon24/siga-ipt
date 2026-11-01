package com.example.siga.dto;

import com.example.siga.entity.StudyPlanStatus;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudyPlanResponseDTO {
    private Long id;
    private String identifier;
    private String name;
    private StudyPlanStatus status;
    private Long careerId;
    private String careerName;
}
