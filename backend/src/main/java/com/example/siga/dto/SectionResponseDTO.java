package com.example.siga.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SectionResponseDTO {
    private Long id;

    private Long subjectId;
    private String subjectCode;
    private String subjectName;

    private Long academicPeriodId;
    private String academicPeriodCode;

    private Long teacherId;
    private String teacherFullName;

    private Integer capacity;
    private List<ScheduleBlockResponseDTO> scheduleBlocks;
}
