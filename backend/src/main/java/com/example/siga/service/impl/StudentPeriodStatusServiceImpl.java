package com.example.siga.service.impl;

import com.example.siga.dto.StudentPeriodStatusRequestDTO;
import com.example.siga.dto.StudentPeriodStatusResponseDTO;
import com.example.siga.entity.AcademicPeriod;
import com.example.siga.entity.AcademicPeriodStatus;
import com.example.siga.entity.Student;
import com.example.siga.entity.StudentPeriodStatus;
import com.example.siga.exception.BusinessException;
import com.example.siga.exception.ResourceNotFoundException;
import com.example.siga.mapper.StudentPeriodStatusMapper;
import com.example.siga.repository.AcademicPeriodRepository;
import com.example.siga.repository.StudentPeriodStatusRepository;
import com.example.siga.repository.StudentRepository;
import com.example.siga.service.StudentPeriodStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudentPeriodStatusServiceImpl implements StudentPeriodStatusService {

    private final StudentPeriodStatusRepository studentPeriodStatusRepository;
    private final StudentRepository studentRepository;
    private final AcademicPeriodRepository academicPeriodRepository;
    private final StudentPeriodStatusMapper mapper;

    @Override
    @Transactional
    public StudentPeriodStatusResponseDTO save(StudentPeriodStatusRequestDTO dto) {
        Student student = studentRepository.findById(dto.getStudentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student not found with id: " + dto.getStudentId()));

        AcademicPeriod period = academicPeriodRepository.findById(dto.getAcademicPeriodId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Academic period not found with id: " + dto.getAcademicPeriodId()));

        // Regla: la matricula de un periodo CERRADO ya no se toca
        if (period.getStatus() != AcademicPeriodStatus.ABIERTO) {
            throw new BusinessException(
                    "Academic period '" + period.getCode() + "' is CERRADO and its matriculation cannot be changed"
            );
        }

        StudentPeriodStatus status = studentPeriodStatusRepository
                .findByStudentIdAndAcademicPeriodId(student.getId(), period.getId())
                .orElseGet(() -> StudentPeriodStatus.builder()
                        .student(student)
                        .academicPeriod(period)
                        .build());

        status.setEnrollmentStatus(dto.getEnrollmentStatus());

        return mapper.toResponseDTO(studentPeriodStatusRepository.save(status));
    }

    @Override
    @Transactional(readOnly = true)
    public StudentPeriodStatusResponseDTO findByStudentAndPeriod(Long studentId, Long academicPeriodId) {
        StudentPeriodStatus status = studentPeriodStatusRepository
                .findByStudentIdAndAcademicPeriodId(studentId, academicPeriodId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student " + studentId + " has no matriculation record for academic period "
                                + academicPeriodId));
        return mapper.toResponseDTO(status);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentPeriodStatusResponseDTO> findByPeriod(Long academicPeriodId) {
        if (!academicPeriodRepository.existsById(academicPeriodId)) {
            throw new ResourceNotFoundException("Academic period not found with id: " + academicPeriodId);
        }
        return studentPeriodStatusRepository.findByAcademicPeriodId(academicPeriodId).stream()
                .map(mapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentPeriodStatusResponseDTO> findByStudent(Long studentId) {
        if (!studentRepository.existsById(studentId)) {
            throw new ResourceNotFoundException("Student not found with id: " + studentId);
        }
        return studentPeriodStatusRepository.findByStudentId(studentId).stream()
                .map(mapper::toResponseDTO)
                .collect(Collectors.toList());
    }
}
