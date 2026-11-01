package com.example.siga.mapper;

import com.example.siga.dto.CareerRequestDTO;
import com.example.siga.dto.CareerResponseDTO;
import com.example.siga.entity.Career;
import org.springframework.stereotype.Component;

@Component
public class CareerMapper {

    public Career toEntity(CareerRequestDTO dto) {
        return Career.builder()
                .code(dto.getCode())
                .name(dto.getName())
                .description(dto.getDescription())
                .status(dto.getStatus())
                .durationSemesters(4)
                .build();
    }

    public CareerResponseDTO toResponseDTO(Career entity) {
        return CareerResponseDTO.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .name(entity.getName())
                .description(entity.getDescription())
                .status(entity.getStatus())
                .durationSemesters(entity.getDurationSemesters())
                .build();
    }

    public void updateEntity(Career entity, CareerRequestDTO dto) {
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setStatus(dto.getStatus());
    }
}
