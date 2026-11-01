package com.example.siga.dto;

import com.example.siga.entity.AcademicHistoryResult;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * En el acta de una seccion tambien aparecen las inscripciones todavia sin nota:
 * en ese caso id, value, result y las fechas vienen nulos.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GradeResponseDTO {
    private Long id;

    private Long enrollmentId;

    private Long studentId;
    private String studentRun;
    private String studentFullName;

    private Long subjectId;
    private String subjectCode;
    private String subjectName;

    private Long sectionId;

    private Long academicPeriodId;
    private String academicPeriodCode;

    private BigDecimal value;
    private AcademicHistoryResult result;

    private LocalDateTime recordedAt;
    private LocalDateTime updatedAt;
}
