package com.example.siga.dto;

import com.example.siga.entity.EnrollmentStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnrollmentResponseDTO {
    private Long id;

    private Long studentId;
    private String studentRun;
    private String studentFullName;

    private Long subjectId;
    private String subjectCode;
    private String subjectName;
    private Integer subjectSemester;

    private Long sectionId;
    private String teacherFullName;
    private List<ScheduleBlockResponseDTO> scheduleBlocks;

    private Long academicPeriodId;
    private String academicPeriodCode;

    private LocalDateTime enrollmentDate;
    private EnrollmentStatus status;
}
