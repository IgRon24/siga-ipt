package com.example.siga.mapper;

import com.example.siga.dto.SubjectRequestDTO;
import com.example.siga.dto.SubjectResponseDTO;
import com.example.siga.entity.Subject;
import org.springframework.stereotype.Component;

@Component
public class SubjectMapper {

    public Subject toEntity(SubjectRequestDTO dto) {
        return Subject.builder()
                .code(dto.getCode())
                .name(dto.getName())
                .semester(dto.getSemester())
                .telTheory(dto.getTelTheory())
                .telExercises(dto.getTelExercises())
                .telLab(dto.getTelLab())
                .sct(dto.getSct())
                .build();
    }

    public SubjectResponseDTO toResponseDTO(Subject entity) {
        return SubjectResponseDTO.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .name(entity.getName())
                .semester(entity.getSemester())
                .telTheory(entity.getTelTheory())
                .telExercises(entity.getTelExercises())
                .telLab(entity.getTelLab())
                .sct(entity.getSct())
                .studyPlanId(entity.getStudyPlan() != null ? entity.getStudyPlan().getId() : null)
                .studyPlanName(entity.getStudyPlan() != null ? entity.getStudyPlan().getName() : null)
                .build();
    }

    public void updateEntity(Subject entity, SubjectRequestDTO dto) {
        entity.setCode(dto.getCode());
        entity.setName(dto.getName());
        entity.setSemester(dto.getSemester());
        entity.setTelTheory(dto.getTelTheory());
        entity.setTelExercises(dto.getTelExercises());
        entity.setTelLab(dto.getTelLab());
        entity.setSct(dto.getSct());
    }
}
