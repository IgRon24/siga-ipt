package com.example.siga.mapper;

import com.example.siga.dto.PrerequisiteResponseDTO;
import com.example.siga.entity.Prerequisite;
import com.example.siga.entity.Subject;
import org.springframework.stereotype.Component;

@Component
public class PrerequisiteMapper {

    public Prerequisite toEntity(Subject subject, Subject prerequisiteSubject) {
        return Prerequisite.builder()
                .subject(subject)
                .prerequisiteSubject(prerequisiteSubject)
                .build();
    }

    public PrerequisiteResponseDTO toResponseDTO(Prerequisite entity) {
        Subject subject = entity.getSubject();
        Subject prerequisite = entity.getPrerequisiteSubject();

        return PrerequisiteResponseDTO.builder()
                .id(entity.getId())
                .subjectId(subject != null ? subject.getId() : null)
                .subjectCode(subject != null ? subject.getCode() : null)
                .prerequisiteSubjectId(prerequisite != null ? prerequisite.getId() : null)
                .prerequisiteSubjectCode(prerequisite != null ? prerequisite.getCode() : null)
                .prerequisiteSubjectName(prerequisite != null ? prerequisite.getName() : null)
                .prerequisiteSubjectSemester(prerequisite != null ? prerequisite.getSemester() : null)
                .build();
    }
}
