package com.example.siga.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubjectResponseDTO {
    private Long id;
    private String code;
    private String name;
    private Integer semester;
    private Integer telTheory;
    private Integer telExercises;
    private Integer telLab;
    private Integer sct;
    private Long studyPlanId;
    private String studyPlanName;
}
