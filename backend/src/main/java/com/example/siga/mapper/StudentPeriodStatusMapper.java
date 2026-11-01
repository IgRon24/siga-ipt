package com.example.siga.mapper;

import com.example.siga.dto.StudentPeriodStatusResponseDTO;
import com.example.siga.entity.StudentPeriodStatus;
import org.springframework.stereotype.Component;

@Component
public class StudentPeriodStatusMapper {

    public StudentPeriodStatusResponseDTO toResponseDTO(StudentPeriodStatus entity) {
        return StudentPeriodStatusResponseDTO.builder()
                .id(entity.getId())
                .studentId(entity.getStudent().getId())
                .studentRun(entity.getStudent().getRun())
                .studentFullName(entity.getStudent().getFullName())
                .academicPeriodId(entity.getAcademicPeriod().getId())
                .academicPeriodCode(entity.getAcademicPeriod().getCode())
                .enrollmentStatus(entity.getEnrollmentStatus())
                .build();
    }
}
