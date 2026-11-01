package com.example.siga.dto;

import lombok.*;

import java.util.List;

/** Una asignatura del plan del estudiante ofrecida en el periodo y disponible para inscribir. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AvailableSectionResponseDTO {
    private Long sectionId;

    private Long subjectId;
    private String subjectCode;
    private String subjectName;
    private Integer subjectSemester;
    private Integer subjectSct;

    private Long teacherId;
    private String teacherFullName;

    private Integer capacity;
    private Long enrolled;
    private Long availableSeats;

    private List<ScheduleBlockResponseDTO> scheduleBlocks;
}
