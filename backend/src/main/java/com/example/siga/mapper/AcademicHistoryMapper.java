package com.example.siga.mapper;

import com.example.siga.dto.AcademicHistoryResponseDTO;
import com.example.siga.entity.AcademicHistory;
import org.springframework.stereotype.Component;

@Component
public class AcademicHistoryMapper {

    public AcademicHistoryResponseDTO toResponseDTO(AcademicHistory entity) {
        return AcademicHistoryResponseDTO.builder()
                .id(entity.getId())
                .studentId(entity.getStudent().getId())
                .studentRun(entity.getStudent().getRun())
                .studentFullName(entity.getStudent().getFullName())
                .subjectCode(entity.getSubjectCode())
                .subjectName(entity.getSubjectName())
                .academicPeriodId(entity.getAcademicPeriod().getId())
                .academicPeriodCode(entity.getAcademicPeriod().getCode())
                .grade(entity.getGrade())
                .result(entity.getResult())
                .build();
    }
}
