package com.example.siga.service;

import com.example.siga.dto.StudentPeriodStatusRequestDTO;
import com.example.siga.dto.StudentPeriodStatusResponseDTO;

import java.util.List;

public interface StudentPeriodStatusService {

    /** Crea o actualiza la matricula de un estudiante en un periodo. */
    StudentPeriodStatusResponseDTO save(StudentPeriodStatusRequestDTO dto);

    StudentPeriodStatusResponseDTO findByStudentAndPeriod(Long studentId, Long academicPeriodId);
    List<StudentPeriodStatusResponseDTO> findByPeriod(Long academicPeriodId);
    List<StudentPeriodStatusResponseDTO> findByStudent(Long studentId);
}
