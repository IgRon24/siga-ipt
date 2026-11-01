package com.example.siga.service;

import com.example.siga.dto.TeacherRequestDTO;
import com.example.siga.dto.TeacherResponseDTO;

import java.util.List;

public interface TeacherService {

    TeacherResponseDTO create(TeacherRequestDTO dto);
    TeacherResponseDTO findById(Long id);
    List<TeacherResponseDTO> findAll();
    List<TeacherResponseDTO> findByStatus(String status);
    TeacherResponseDTO findByEmail(String email);
    TeacherResponseDTO update(Long id, TeacherRequestDTO dto);
    TeacherResponseDTO changeStatus(Long id, String newStatus);
    void delete(Long id);
}
