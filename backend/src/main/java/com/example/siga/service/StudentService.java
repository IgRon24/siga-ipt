package com.example.siga.service;

import com.example.siga.dto.StudentRequestDTO;
import com.example.siga.dto.StudentResponseDTO;

import java.util.List;

public interface StudentService {

    StudentResponseDTO create(StudentRequestDTO dto);
    StudentResponseDTO findById(Long id);
    List<StudentResponseDTO> findAll();
    List<StudentResponseDTO> findByStatus(String status);
    List<StudentResponseDTO> findByCareer(Long careerId);
    StudentResponseDTO findByEmail(String email);
    StudentResponseDTO update(Long id, StudentRequestDTO dto);
    StudentResponseDTO changeStatus(Long id, String newStatus);
    void delete(Long id);
}
