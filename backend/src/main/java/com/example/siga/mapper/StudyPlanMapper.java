package com.example.siga.mapper;

import com.example.siga.dto.StudyPlanRequestDTO;
import com.example.siga.dto.StudyPlanResponseDTO;
import com.example.siga.entity.StudyPlan;
import org.springframework.stereotype.Component;

@Component
public class StudyPlanMapper {

    public StudyPlan toEntity(StudyPlanRequestDTO dto) {
        return StudyPlan.builder()
                .identifier(dto.getIdentifier())
                .name(dto.getName())
                .status(dto.getStatus())
                .build();
    }

    public StudyPlanResponseDTO toResponseDTO(StudyPlan entity) {
        return StudyPlanResponseDTO.builder()
                .id(entity.getId())
                .identifier(entity.getIdentifier())
                .name(entity.getName())
                .status(entity.getStatus())
                .careerId(entity.getCareer() != null ? entity.getCareer().getId() : null)
                .careerName(entity.getCareer() != null ? entity.getCareer().getName() : null)
                .build();
    }

    public void updateEntity(StudyPlan entity, StudyPlanRequestDTO dto) {
        entity.setIdentifier(dto.getIdentifier());
        entity.setName(dto.getName());
        entity.setStatus(dto.getStatus());
    }
}
