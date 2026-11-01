package com.example.siga.mapper;

import com.example.siga.dto.AcademicPeriodRequestDTO;
import com.example.siga.dto.AcademicPeriodResponseDTO;
import com.example.siga.entity.AcademicPeriod;
import org.springframework.stereotype.Component;

@Component
public class AcademicPeriodMapper {

    /** El codigo y el estado los pone el servicio. */
    public AcademicPeriod toEntity(AcademicPeriodRequestDTO dto) {
        return AcademicPeriod.builder()
                .year(dto.getYear())
                .semester(dto.getSemester())
                .build();
    }

    public AcademicPeriodResponseDTO toResponseDTO(AcademicPeriod entity) {
        return AcademicPeriodResponseDTO.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .year(entity.getYear())
                .semester(entity.getSemester())
                .status(entity.getStatus())
                .build();
    }

    public void updateEntity(AcademicPeriod entity, AcademicPeriodRequestDTO dto) {
        entity.setYear(dto.getYear());
        entity.setSemester(dto.getSemester());
    }
}
