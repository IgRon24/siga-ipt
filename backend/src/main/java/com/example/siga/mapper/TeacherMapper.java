package com.example.siga.mapper;

import com.example.siga.dto.TeacherRequestDTO;
import com.example.siga.dto.TeacherResponseDTO;
import com.example.siga.entity.Teacher;
import org.springframework.stereotype.Component;

@Component
public class TeacherMapper {

    public Teacher toEntity(TeacherRequestDTO dto) {
        return Teacher.builder()
                .run(dto.getRun())
                .fullName(dto.getFullName())
                .email(dto.getEmail())
                .professionalTitle(dto.getProfessionalTitle())
                .academicDegree(dto.getAcademicDegree())
                .build();
    }

    public TeacherResponseDTO toResponseDTO(Teacher entity) {
        return TeacherResponseDTO.builder()
                .id(entity.getId())
                .run(entity.getRun())
                .fullName(entity.getFullName())
                .email(entity.getEmail())
                .professionalTitle(entity.getProfessionalTitle())
                .academicDegree(entity.getAcademicDegree())
                .status(entity.getStatus())
                .build();
    }

    /** El estado no se toca aqui: cambia solo por PATCH /{id}/status. */
    public void updateEntity(Teacher entity, TeacherRequestDTO dto) {
        entity.setRun(dto.getRun());
        entity.setFullName(dto.getFullName());
        entity.setEmail(dto.getEmail());
        entity.setProfessionalTitle(dto.getProfessionalTitle());
        entity.setAcademicDegree(dto.getAcademicDegree());
    }
}
