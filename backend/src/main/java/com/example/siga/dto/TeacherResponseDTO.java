package com.example.siga.dto;

import com.example.siga.entity.TeacherStatus;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeacherResponseDTO {
    private Long id;
    private String run;
    private String fullName;
    private String email;
    private String professionalTitle;
    private String academicDegree;
    private TeacherStatus status;
}
