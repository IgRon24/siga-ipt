package com.example.siga.service.impl;

import com.example.siga.dto.StudentRequestDTO;
import com.example.siga.dto.StudentResponseDTO;
import com.example.siga.entity.Career;
import com.example.siga.entity.CareerStatus;
import com.example.siga.entity.Student;
import com.example.siga.entity.StudentStatus;
import com.example.siga.entity.StudyPlan;
import com.example.siga.entity.StudyPlanStatus;
import com.example.siga.exception.BusinessException;
import com.example.siga.exception.ResourceNotFoundException;
import com.example.siga.mapper.StudentMapper;
import com.example.siga.repository.AcademicHistoryRepository;
import com.example.siga.repository.CareerRepository;
import com.example.siga.repository.EnrollmentRepository;
import com.example.siga.repository.StudentPeriodStatusRepository;
import com.example.siga.repository.StudentRepository;
import com.example.siga.repository.StudyPlanRepository;
import com.example.siga.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudentServiceImpl implements StudentService {

    /**
     * Estados que el Administrador Academico puede asignar a mano.
     * EGRESADO y ELIMINADO los determina el cierre de periodo, no el usuario.
     */
    private static final Set<StudentStatus> MANUAL_STATUSES = EnumSet.of(
            StudentStatus.REGULAR,
            StudentStatus.POSTERGACION,
            StudentStatus.RETIRO_TEMPORAL
    );

    private final StudentRepository studentRepository;
    private final CareerRepository careerRepository;
    private final StudyPlanRepository studyPlanRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AcademicHistoryRepository academicHistoryRepository;
    private final StudentPeriodStatusRepository studentPeriodStatusRepository;
    private final StudentMapper mapper;

    @Override
    @Transactional
    public StudentResponseDTO create(StudentRequestDTO dto) {
        // Regla: RUN unico en el sistema
        if (studentRepository.existsByRun(dto.getRun())) {
            throw new BusinessException("A student with RUN '" + dto.getRun() + "' already exists");
        }

        // Regla: email unico en el sistema
        if (studentRepository.existsByEmail(dto.getEmail())) {
            throw new BusinessException("A student with email '" + dto.getEmail() + "' already exists");
        }

        Career career = findCareerOrThrow(dto.getCareerId());

        // Regla: una carrera INACTIVA no recibe nuevos estudiantes
        requireActiveCareer(career);

        // Regla: el estudiante queda asociado al plan VIGENTE de su carrera
        StudyPlan studyPlan = findCurrentPlanOrThrow(career);

        Student student = mapper.toEntity(dto);
        student.setCareer(career);
        student.setStudyPlan(studyPlan);

        // Regla: todo estudiante queda inicialmente en estado REGULAR
        student.setStatus(StudentStatus.REGULAR);

        return mapper.toResponseDTO(studentRepository.save(student));
    }

    @Override
    @Transactional(readOnly = true)
    public StudentResponseDTO findById(Long id) {
        return mapper.toResponseDTO(findStudentOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentResponseDTO> findAll() {
        return studentRepository.findAll().stream()
                .map(mapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentResponseDTO> findByStatus(String status) {
        return studentRepository.findByStatus(parseStatus(status)).stream()
                .map(mapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentResponseDTO> findByCareer(Long careerId) {
        if (!careerRepository.existsById(careerId)) {
            throw new ResourceNotFoundException("Career not found with id: " + careerId);
        }
        return studentRepository.findByCareerId(careerId).stream()
                .map(mapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    /** Usado por GET /api/students/me: el email del token identifica al estudiante. */
    @Override
    @Transactional(readOnly = true)
    public StudentResponseDTO findByEmail(String email) {
        Student student = studentRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with email: " + email));
        return mapper.toResponseDTO(student);
    }

    @Override
    @Transactional
    public StudentResponseDTO update(Long id, StudentRequestDTO dto) {
        Student student = findStudentOrThrow(id);

        // Regla: el RUN no puede quedar igual al de otro estudiante
        if (!student.getRun().equals(dto.getRun())
                && studentRepository.existsByRun(dto.getRun())) {
            throw new BusinessException("A student with RUN '" + dto.getRun() + "' already exists");
        }

        // Regla: el email no puede quedar igual al de otro estudiante
        if (!student.getEmail().equals(dto.getEmail())
                && studentRepository.existsByEmail(dto.getEmail())) {
            throw new BusinessException("A student with email '" + dto.getEmail() + "' already exists");
        }

        // Regla: la carrera (y con ella el plan) solo se puede cambiar mientras el
        // estudiante no tenga inscripciones ni historial academico
        if (!student.getCareer().getId().equals(dto.getCareerId())) {
            requireNoAcademicRecord(student, "cannot change career or study plan");

            Career career = findCareerOrThrow(dto.getCareerId());
            requireActiveCareer(career);
            student.setCareer(career);
            student.setStudyPlan(findCurrentPlanOrThrow(career));
        }

        mapper.updateEntity(student, dto);

        return mapper.toResponseDTO(studentRepository.save(student));
    }

    @Override
    @Transactional
    public StudentResponseDTO changeStatus(Long id, String newStatus) {
        Student student = findStudentOrThrow(id);

        StudentStatus status = parseStatus(newStatus);

        // Regla: EGRESADO y ELIMINADO no se asignan manualmente
        if (!MANUAL_STATUSES.contains(status)) {
            throw new BusinessException(
                    "Status " + status + " is assigned automatically during the academic period closure"
            );
        }

        // Regla: ELIMINADO no vuelve a REGULAR; EGRESADO tampoco se revierte
        if (!MANUAL_STATUSES.contains(student.getStatus())) {
            throw new BusinessException(
                    "A student in status " + student.getStatus() + " cannot change status"
            );
        }

        student.setStatus(status);
        return mapper.toResponseDTO(studentRepository.save(student));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Student student = findStudentOrThrow(id);

        // Regla: un estudiante con inscripciones o historial academico no se elimina fisicamente
        requireNoAcademicRecord(student, "cannot be deleted");

        // La matricula no es informacion academica: se va junto con el estudiante
        studentPeriodStatusRepository.deleteByStudentId(id);
        studentRepository.delete(student);
    }

    /** Inscripciones e historial academico bloquean el borrado y el cambio de carrera. */
    private void requireNoAcademicRecord(Student student, String action) {
        if (enrollmentRepository.existsByStudentId(student.getId())) {
            throw new BusinessException(
                    "Student '" + student.getRun() + "' has enrollments and " + action
            );
        }

        if (academicHistoryRepository.existsByStudentId(student.getId())) {
            throw new BusinessException(
                    "Student '" + student.getRun() + "' has academic history and " + action
            );
        }
    }

    private Student findStudentOrThrow(Long id) {
        return studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with id: " + id));
    }

    private Career findCareerOrThrow(Long careerId) {
        return careerRepository.findById(careerId)
                .orElseThrow(() -> new ResourceNotFoundException("Career not found with id: " + careerId));
    }

    private void requireActiveCareer(Career career) {
        if (career.getStatus() != CareerStatus.ACTIVA) {
            throw new BusinessException(
                    "Career '" + career.getCode() + "' is INACTIVA and cannot receive students"
            );
        }
    }

    private StudyPlan findCurrentPlanOrThrow(Career career) {
        return studyPlanRepository.findByCareerIdAndStatus(career.getId(), StudyPlanStatus.VIGENTE)
                .orElseThrow(() -> new BusinessException(
                        "Career '" + career.getCode() + "' has no VIGENTE study plan"
                ));
    }

    private StudentStatus parseStatus(String status) {
        if (status == null) {
            throw new BusinessException("Status is required");
        }
        try {
            return StudentStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Invalid status: " + status);
        }
    }
}
