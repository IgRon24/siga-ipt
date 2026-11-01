package com.example.siga.mapper;

import com.example.siga.dto.AvailableSectionResponseDTO;
import com.example.siga.dto.EnrollmentResponseDTO;
import com.example.siga.dto.ScheduleBlockResponseDTO;
import com.example.siga.entity.Enrollment;
import com.example.siga.entity.ScheduleBlock;
import com.example.siga.entity.Section;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class EnrollmentMapper {

    public EnrollmentResponseDTO toResponseDTO(Enrollment entity) {
        Section section = entity.getSection();
        return EnrollmentResponseDTO.builder()
                .id(entity.getId())
                .studentId(entity.getStudent().getId())
                .studentRun(entity.getStudent().getRun())
                .studentFullName(entity.getStudent().getFullName())
                .subjectId(entity.getSubject().getId())
                .subjectCode(entity.getSubject().getCode())
                .subjectName(entity.getSubject().getName())
                .subjectSemester(entity.getSubject().getSemester())
                .sectionId(section != null ? section.getId() : null)
                .teacherFullName(section != null ? section.getTeacher().getFullName() : null)
                .scheduleBlocks(section != null ? toScheduleBlocks(section.getScheduleBlocks()) : null)
                .academicPeriodId(entity.getAcademicPeriod().getId())
                .academicPeriodCode(entity.getAcademicPeriod().getCode())
                .enrollmentDate(entity.getEnrollmentDate())
                .status(entity.getStatus())
                .build();
    }

    public AvailableSectionResponseDTO toAvailableDTO(Section section, long enrolled) {
        return AvailableSectionResponseDTO.builder()
                .sectionId(section.getId())
                .subjectId(section.getSubject().getId())
                .subjectCode(section.getSubject().getCode())
                .subjectName(section.getSubject().getName())
                .subjectSemester(section.getSubject().getSemester())
                .subjectSct(section.getSubject().getSct())
                .teacherId(section.getTeacher().getId())
                .teacherFullName(section.getTeacher().getFullName())
                .capacity(section.getCapacity())
                .enrolled(enrolled)
                .availableSeats(section.getCapacity() - enrolled)
                .scheduleBlocks(toScheduleBlocks(section.getScheduleBlocks()))
                .build();
    }

    private List<ScheduleBlockResponseDTO> toScheduleBlocks(List<ScheduleBlock> blocks) {
        return blocks.stream()
                .map(block -> ScheduleBlockResponseDTO.builder()
                        .id(block.getId())
                        .day(block.getDay())
                        .module(block.getModule())
                        .build())
                .collect(Collectors.toList());
    }
}
