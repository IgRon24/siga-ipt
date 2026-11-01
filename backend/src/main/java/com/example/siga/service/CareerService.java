package com.example.siga.service;

import com.example.siga.dto.CareerRequestDTO;
import com.example.siga.dto.CareerResponseDTO;

import java.util.List;

public interface CareerService {

    CareerResponseDTO create(CareerRequestDTO dto);

    CareerResponseDTO findById(Long id);

    List<CareerResponseDTO> findAll();

    CareerResponseDTO update(Long id, CareerRequestDTO dto);

    void delete(Long id);
}
