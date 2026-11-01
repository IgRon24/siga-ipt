package com.example.siga.mapper;

import com.example.siga.dto.GradeResponseDTO;
import com.example.siga.entity.Enrollment;
import com.example.siga.entity.Grade;
import org.springframework.stereotype.Component;

@Component
public class GradeMapper {

    public GradeResponseDTO toResponseDTO(Grade entity) {
        GradeResponseDTO dto = pendingFor(entity.getEnrollment());
        dto.setId(entity.getId());
        dto.setValue(entity.getValue());
        dto.setResult(entity.getResult());
        dto.setRecordedAt(entity.getRecordedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    /** Fila del acta para una inscripcion que todavia no tiene nota. */
    public GradeResponseDTO pendingFor(Enrollment enrollment) {
        return GradeResponseDTO.builder()
                .enrollmentId(enrollment.getId())
                .studentId(enrollment.getStudent().getId())
                .studentRun(enrollment.getStudent().getRun())
                .studentFullName(enrollment.getStudent().getFullName())
                .subjectId(enrollment.getSubject().getId())
                .subjectCode(enrollment.getSubject().getCode())
                .subjectName(enrollment.getSubject().getName())
                .sectionId(enrollment.getSection().getId())
                .academicPeriodId(enrollment.getAcademicPeriod().getId())
                .academicPeriodCode(enrollment.getAcademicPeriod().getCode())
                .build();
    }
}
