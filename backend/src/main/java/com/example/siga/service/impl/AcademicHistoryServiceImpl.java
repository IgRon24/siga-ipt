package com.example.siga.service.impl;

import com.example.siga.dto.AcademicHistoryResponseDTO;
import com.example.siga.entity.AcademicHistory;
import com.example.siga.exception.ResourceNotFoundException;
import com.example.siga.mapper.AcademicHistoryMapper;
import com.example.siga.repository.AcademicHistoryRepository;
import com.example.siga.repository.StudentRepository;
import com.example.siga.service.AcademicHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AcademicHistoryServiceImpl implements AcademicHistoryService {

    private final AcademicHistoryRepository academicHistoryRepository;
    private final StudentRepository studentRepository;
    private final AcademicHistoryMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<AcademicHistoryResponseDTO> findByStudent(Long studentId) {
        if (!studentRepository.existsById(studentId)) {
            throw new ResourceNotFoundException("Student not found with id: " + studentId);
        }

        return academicHistoryRepository.findByStudentId(studentId).stream()
                .sorted(Comparator
                        .comparing((AcademicHistory record) -> record.getAcademicPeriod().getCode())
                        .thenComparing(AcademicHistory::getSubjectCode))
                .map(mapper::toResponseDTO)
                .collect(Collectors.toList());
    }
}
