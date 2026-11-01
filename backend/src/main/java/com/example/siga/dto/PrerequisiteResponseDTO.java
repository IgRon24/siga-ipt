package com.example.siga.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrerequisiteResponseDTO {
    private Long id;
    private Long subjectId;
    private String subjectCode;
    private Long prerequisiteSubjectId;
    private String prerequisiteSubjectCode;
    private String prerequisiteSubjectName;
    private Integer prerequisiteSubjectSemester;
}
