package com.example.siga.mapper;

import com.example.siga.dto.StudentRequestDTO;
import com.example.siga.dto.StudentResponseDTO;
import com.example.siga.entity.Student;
import org.springframework.stereotype.Component;

@Component
public class StudentMapper {

    /** La carrera y el plan de estudios los resuelve el servicio, no el mapper. */
    public Student toEntity(StudentRequestDTO dto) {
        return Student.builder()
                .run(dto.getRun())
                .fullName(dto.getFullName())
                .email(dto.getEmail())
                .build();
    }

    public StudentResponseDTO toResponseDTO(Student entity) {
        return StudentResponseDTO.builder()
                .id(entity.getId())
                .run(entity.getRun())
                .fullName(entity.getFullName())
                .email(entity.getEmail())
                .status(entity.getStatus())
                .careerId(entity.getCareer() != null ? entity.getCareer().getId() : null)
                .careerCode(entity.getCareer() != null ? entity.getCareer().getCode() : null)
                .careerName(entity.getCareer() != null ? entity.getCareer().getName() : null)
                .studyPlanId(entity.getStudyPlan() != null ? entity.getStudyPlan().getId() : null)
                .studyPlanIdentifier(entity.getStudyPlan() != null ? entity.getStudyPlan().getIdentifier() : null)
                .build();
    }

    /** El estado, la carrera y el plan no se tocan aqui: los maneja el servicio. */
    public void updateEntity(Student entity, StudentRequestDTO dto) {
        entity.setRun(dto.getRun());
        entity.setFullName(dto.getFullName());
        entity.setEmail(dto.getEmail());
    }
}
