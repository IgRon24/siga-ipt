package com.example.siga.mapper;

import com.example.siga.dto.ScheduleBlockRequestDTO;
import com.example.siga.dto.ScheduleBlockResponseDTO;
import com.example.siga.dto.SectionRequestDTO;
import com.example.siga.dto.SectionResponseDTO;
import com.example.siga.entity.ScheduleBlock;
import com.example.siga.entity.Section;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class SectionMapper {

    /** La asignatura, el periodo y el docente los resuelve el servicio. */
    public Section toEntity(SectionRequestDTO dto) {
        return Section.builder()
                .capacity(dto.getCapacity())
                .build();
    }

    /** Los bloques quedan sin seccion: la asigna {@code Section.replaceScheduleBlocks}. */
    public List<ScheduleBlock> toScheduleBlocks(List<ScheduleBlockRequestDTO> dtos) {
        return dtos.stream()
                .map(dto -> ScheduleBlock.builder()
                        .day(dto.getDay())
                        .module(dto.getModule())
                        .build())
                .collect(Collectors.toList());
    }

    public SectionResponseDTO toResponseDTO(Section entity) {
        return SectionResponseDTO.builder()
                .id(entity.getId())
                .subjectId(entity.getSubject() != null ? entity.getSubject().getId() : null)
                .subjectCode(entity.getSubject() != null ? entity.getSubject().getCode() : null)
                .subjectName(entity.getSubject() != null ? entity.getSubject().getName() : null)
                .academicPeriodId(entity.getAcademicPeriod() != null ? entity.getAcademicPeriod().getId() : null)
                .academicPeriodCode(entity.getAcademicPeriod() != null ? entity.getAcademicPeriod().getCode() : null)
                .teacherId(entity.getTeacher() != null ? entity.getTeacher().getId() : null)
                .teacherFullName(entity.getTeacher() != null ? entity.getTeacher().getFullName() : null)
                .capacity(entity.getCapacity())
                .scheduleBlocks(entity.getScheduleBlocks().stream()
                        .map(this::toScheduleBlockResponseDTO)
                        .collect(Collectors.toList()))
                .build();
    }

    public void updateEntity(Section entity, SectionRequestDTO dto) {
        entity.setCapacity(dto.getCapacity());
    }

    private ScheduleBlockResponseDTO toScheduleBlockResponseDTO(ScheduleBlock block) {
        return ScheduleBlockResponseDTO.builder()
                .id(block.getId())
                .day(block.getDay())
                .module(block.getModule())
                .build();
    }
}
