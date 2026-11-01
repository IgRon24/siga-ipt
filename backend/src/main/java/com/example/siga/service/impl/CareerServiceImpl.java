package com.example.siga.service.impl;

import com.example.siga.dto.CareerRequestDTO;
import com.example.siga.dto.CareerResponseDTO;
import com.example.siga.entity.Career;
import com.example.siga.exception.BusinessException;
import com.example.siga.exception.ResourceNotFoundException;
import com.example.siga.mapper.CareerMapper;
import com.example.siga.repository.CareerRepository;
import com.example.siga.repository.StudentRepository;
import com.example.siga.repository.StudyPlanRepository;
import com.example.siga.service.CareerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CareerServiceImpl implements CareerService {

    private final CareerRepository careerRepository;
    private final StudyPlanRepository studyPlanRepository;
    private final StudentRepository studentRepository;
    private final CareerMapper careerMapper;

    @Override
    @Transactional
    public CareerResponseDTO create(CareerRequestDTO dto) {
        // Regla: código único
        if (careerRepository.existsByCode(dto.getCode())) {
            throw new BusinessException("A career with code '" + dto.getCode() + "' already exists");
        }

        Career career = careerMapper.toEntity(dto);
        return careerMapper.toResponseDTO(careerRepository.save(career));
    }

    @Override
    @Transactional(readOnly = true)
    public CareerResponseDTO findById(Long id) {
        Career career = careerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Career not found with id: " + id));
        return careerMapper.toResponseDTO(career);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CareerResponseDTO> findAll() {
        return careerRepository.findAll().stream()
                .map(careerMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public CareerResponseDTO update(Long id, CareerRequestDTO dto) {
        Career career = careerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Career not found with id: " + id));

        // Regla: el código no puede ser modificado a uno ya existente
        if (!career.getCode().equals(dto.getCode())
                && careerRepository.existsByCode(dto.getCode())) {
            throw new BusinessException("A career with code '" + dto.getCode() + "' already exists");
        }

        career.setCode(dto.getCode());
        careerMapper.updateEntity(career, dto);

        return careerMapper.toResponseDTO(careerRepository.save(career));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Career career = careerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Career not found with id: " + id));

        // Regla: una carrera con estudiantes o planes asociados no se puede eliminar;
        // solo se puede cambiar su estado a INACTIVA
        if (studentRepository.existsByCareerId(id)) {
            throw new BusinessException(
                    "Career '" + career.getCode() + "' has students associated and cannot be deleted"
            );
        }

        if (studyPlanRepository.existsByCareerId(id)) {
            throw new BusinessException(
                    "Career '" + career.getCode() + "' has study plans associated and cannot be deleted"
            );
        }

        careerRepository.delete(career);
    }
}
