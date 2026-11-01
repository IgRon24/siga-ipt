package com.example.siga.service.impl;

import com.example.siga.dto.TeacherRequestDTO;
import com.example.siga.dto.TeacherResponseDTO;
import com.example.siga.entity.AcademicPeriodStatus;
import com.example.siga.entity.Teacher;
import com.example.siga.entity.TeacherStatus;
import com.example.siga.exception.BusinessException;
import com.example.siga.exception.ResourceNotFoundException;
import com.example.siga.mapper.TeacherMapper;
import com.example.siga.repository.SectionRepository;
import com.example.siga.repository.TeacherRepository;
import com.example.siga.service.TeacherService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeacherServiceImpl implements TeacherService {

    private final TeacherRepository teacherRepository;
    private final SectionRepository sectionRepository;
    private final TeacherMapper mapper;

    @Override
    @Transactional
    public TeacherResponseDTO create(TeacherRequestDTO dto) {
        // Regla: RUN unico en el sistema
        if (teacherRepository.existsByRun(dto.getRun())) {
            throw new BusinessException("A teacher with RUN '" + dto.getRun() + "' already exists");
        }

        // Regla: email unico en el sistema
        if (teacherRepository.existsByEmail(dto.getEmail())) {
            throw new BusinessException("A teacher with email '" + dto.getEmail() + "' already exists");
        }

        Teacher teacher = mapper.toEntity(dto);

        // Regla: todo docente queda inicialmente en estado ACTIVO
        teacher.setStatus(TeacherStatus.ACTIVO);

        return mapper.toResponseDTO(teacherRepository.save(teacher));
    }

    @Override
    @Transactional(readOnly = true)
    public TeacherResponseDTO findById(Long id) {
        Teacher teacher = teacherRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found with id: " + id));
        return mapper.toResponseDTO(teacher);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeacherResponseDTO> findAll() {
        return teacherRepository.findAll().stream()
                .map(mapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeacherResponseDTO> findByStatus(String status) {
        return teacherRepository.findByStatus(parseStatus(status)).stream()
                .map(mapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    /** Identifica al docente responsable a partir del email del token. */
    @Override
    @Transactional(readOnly = true)
    public TeacherResponseDTO findByEmail(String email) {
        Teacher teacher = teacherRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found with email: " + email));
        return mapper.toResponseDTO(teacher);
    }

    @Override
    @Transactional
    public TeacherResponseDTO update(Long id, TeacherRequestDTO dto) {
        Teacher teacher = teacherRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found with id: " + id));

        // Regla: el RUN no puede quedar igual al de otro docente
        if (!teacher.getRun().equals(dto.getRun())
                && teacherRepository.existsByRun(dto.getRun())) {
            throw new BusinessException("A teacher with RUN '" + dto.getRun() + "' already exists");
        }

        // Regla: el email no puede quedar igual al de otro docente
        if (!teacher.getEmail().equals(dto.getEmail())
                && teacherRepository.existsByEmail(dto.getEmail())) {
            throw new BusinessException("A teacher with email '" + dto.getEmail() + "' already exists");
        }

        mapper.updateEntity(teacher, dto);

        return mapper.toResponseDTO(teacherRepository.save(teacher));
    }

    @Override
    @Transactional
    public TeacherResponseDTO changeStatus(Long id, String newStatus) {
        Teacher teacher = teacherRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found with id: " + id));

        TeacherStatus status = parseStatus(newStatus);

        // Regla: no puede pasar a INACTIVO con secciones en un periodo ABIERTO.
        // El cambio de estado no altera las secciones ya impartidas.
        if (status == TeacherStatus.INACTIVO
                && sectionRepository.existsByTeacherIdAndAcademicPeriodStatus(id, AcademicPeriodStatus.ABIERTO)) {
            throw new BusinessException(
                    "Teacher '" + teacher.getRun() + "' has sections in an ABIERTO academic period"
                            + " and cannot be set to INACTIVO"
            );
        }

        teacher.setStatus(status);
        return mapper.toResponseDTO(teacherRepository.save(teacher));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Teacher teacher = teacherRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found with id: " + id));

        // Regla: un docente con secciones asociadas no se puede eliminar
        if (sectionRepository.existsByTeacherId(id)) {
            throw new BusinessException(
                    "Teacher '" + teacher.getRun() + "' has sections associated and cannot be deleted"
            );
        }

        teacherRepository.delete(teacher);
    }

    private TeacherStatus parseStatus(String status) {
        if (status == null) {
            throw new BusinessException("Status is required");
        }
        try {
            return TeacherStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Invalid status: " + status);
        }
    }
}
