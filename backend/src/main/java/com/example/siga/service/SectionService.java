package com.example.siga.service;

import com.example.siga.dto.ScheduleBlockRequestDTO;
import com.example.siga.dto.SectionRequestDTO;
import com.example.siga.dto.SectionResponseDTO;

import java.util.List;

public interface SectionService {

    SectionResponseDTO create(SectionRequestDTO dto);
    SectionResponseDTO findById(Long id);
    List<SectionResponseDTO> findAll();
    List<SectionResponseDTO> findByAcademicPeriod(Long academicPeriodId);
    List<SectionResponseDTO> findByTeacher(Long teacherId);
    SectionResponseDTO update(Long id, SectionRequestDTO dto);
    SectionResponseDTO replaceSchedule(Long id, List<ScheduleBlockRequestDTO> blocks);
    void delete(Long id);
}
