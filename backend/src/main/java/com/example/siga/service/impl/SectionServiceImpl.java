package com.example.siga.service.impl;

import com.example.siga.dto.ScheduleBlockRequestDTO;
import com.example.siga.dto.SectionRequestDTO;
import com.example.siga.dto.SectionResponseDTO;
import com.example.siga.entity.AcademicPeriod;
import com.example.siga.entity.AcademicPeriodStatus;
import com.example.siga.entity.EnrollmentStatus;
import com.example.siga.entity.ScheduleBlock;
import com.example.siga.entity.Section;
import com.example.siga.entity.Subject;
import com.example.siga.entity.Teacher;
import com.example.siga.entity.TeacherStatus;
import com.example.siga.exception.BusinessException;
import com.example.siga.exception.ResourceNotFoundException;
import com.example.siga.mapper.SectionMapper;
import com.example.siga.repository.AcademicPeriodRepository;
import com.example.siga.repository.EnrollmentRepository;
import com.example.siga.repository.SectionRepository;
import com.example.siga.repository.SubjectRepository;
import com.example.siga.repository.TeacherRepository;
import com.example.siga.service.SectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SectionServiceImpl implements SectionService {

    /** Cada bloque horario equivale a 2 horas pedagogicas. */
    private static final int HOURS_PER_BLOCK = 2;

    /** Modulos de la jornada academica: 1 a 6. */
    private static final int MAX_MODULE = 6;

    private final SectionRepository sectionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final SubjectRepository subjectRepository;
    private final AcademicPeriodRepository academicPeriodRepository;
    private final TeacherRepository teacherRepository;
    private final SectionMapper mapper;

    @Override
    @Transactional
    public SectionResponseDTO create(SectionRequestDTO dto) {
        Subject subject = findSubjectOrThrow(dto.getSubjectId());
        AcademicPeriod period = findPeriodOrThrow(dto.getAcademicPeriodId());

        // Regla: la oferta academica solo se arma sobre un periodo ABIERTO
        requireOpenPeriod(period);

        // Regla: una asignatura tiene una unica seccion por periodo academico
        if (sectionRepository.existsBySubjectIdAndAcademicPeriodId(subject.getId(), period.getId())) {
            throw new BusinessException(
                    "Subject '" + subject.getCode() + "' already has a section in academic period '"
                            + period.getCode() + "'"
            );
        }

        Teacher teacher = findTeacherOrThrow(dto.getTeacherId());

        // Regla: el docente responsable debe estar ACTIVO al momento de la asignacion
        requireActiveTeacher(teacher);

        Section section = mapper.toEntity(dto);
        section.setSubject(subject);
        section.setAcademicPeriod(period);
        section.setTeacher(teacher);

        List<ScheduleBlock> blocks = mapper.toScheduleBlocks(dto.getScheduleBlocks());
        requireValidSchedule(subject, blocks);
        requireTeacherHasNoConflict(teacher, period, blocks, null);
        section.replaceScheduleBlocks(blocks);

        return mapper.toResponseDTO(sectionRepository.save(section));
    }

    @Override
    @Transactional(readOnly = true)
    public SectionResponseDTO findById(Long id) {
        return mapper.toResponseDTO(findSectionOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SectionResponseDTO> findAll() {
        return sectionRepository.findAll().stream()
                .map(mapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SectionResponseDTO> findByAcademicPeriod(Long academicPeriodId) {
        if (!academicPeriodRepository.existsById(academicPeriodId)) {
            throw new ResourceNotFoundException("Academic period not found with id: " + academicPeriodId);
        }
        return sectionRepository.findByAcademicPeriodId(academicPeriodId).stream()
                .map(mapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SectionResponseDTO> findByTeacher(Long teacherId) {
        if (!teacherRepository.existsById(teacherId)) {
            throw new ResourceNotFoundException("Teacher not found with id: " + teacherId);
        }
        return sectionRepository.findByTeacherId(teacherId).stream()
                .map(mapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public SectionResponseDTO update(Long id, SectionRequestDTO dto) {
        Section section = findSectionOrThrow(id);

        // Regla: no se modifica la oferta de un periodo CERRADO
        requireOpenPeriod(section.getAcademicPeriod());

        Subject subject = findSubjectOrThrow(dto.getSubjectId());
        AcademicPeriod period = findPeriodOrThrow(dto.getAcademicPeriodId());
        requireOpenPeriod(period);

        // Regla: la asignatura sigue teniendo una unica seccion por periodo
        boolean offerChanged = !section.getSubject().getId().equals(subject.getId())
                || !section.getAcademicPeriod().getId().equals(period.getId());
        if (offerChanged
                && sectionRepository.existsBySubjectIdAndAcademicPeriodId(subject.getId(), period.getId())) {
            throw new BusinessException(
                    "Subject '" + subject.getCode() + "' already has a section in academic period '"
                            + period.getCode() + "'"
            );
        }

        Teacher teacher = findTeacherOrThrow(dto.getTeacherId());

        // Regla: el ACTIVO se exige al asignar un docente; cambiar de estado no
        // altera las secciones que ya tenia asignadas
        if (!section.getTeacher().getId().equals(teacher.getId())) {
            requireActiveTeacher(teacher);
        }

        List<ScheduleBlock> blocks = mapper.toScheduleBlocks(dto.getScheduleBlocks());
        requireValidSchedule(subject, blocks);
        requireTeacherHasNoConflict(teacher, period, blocks, section.getId());

        mapper.updateEntity(section, dto);
        section.setSubject(subject);
        section.setAcademicPeriod(period);
        section.setTeacher(teacher);
        section.replaceScheduleBlocks(blocks);

        return mapper.toResponseDTO(sectionRepository.save(section));
    }

    @Override
    @Transactional
    public SectionResponseDTO replaceSchedule(Long id, List<ScheduleBlockRequestDTO> blockDtos) {
        Section section = findSectionOrThrow(id);

        requireOpenPeriod(section.getAcademicPeriod());

        if (blockDtos == null || blockDtos.isEmpty()) {
            throw new BusinessException("At least one schedule block is required");
        }

        List<ScheduleBlock> blocks = mapper.toScheduleBlocks(blockDtos);
        requireValidSchedule(section.getSubject(), blocks);
        requireTeacherHasNoConflict(section.getTeacher(), section.getAcademicPeriod(), blocks, section.getId());

        section.replaceScheduleBlocks(blocks);

        return mapper.toResponseDTO(sectionRepository.save(section));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Section section = findSectionOrThrow(id);

        requireOpenPeriod(section.getAcademicPeriod());

        // Regla: una seccion con estudiantes inscritos no se elimina
        if (enrollmentRepository.existsBySectionIdAndStatus(id, EnrollmentStatus.INSCRITA)) {
            throw new BusinessException(
                    "Section of subject '" + section.getSubject().getCode()
                            + "' has enrolled students and cannot be deleted"
            );
        }

        // Las inscripciones anuladas siguen apuntando a la seccion, asi que tampoco
        // se puede borrar: quedaria el registro huerfano.
        if (enrollmentRepository.existsBySectionId(id)) {
            throw new BusinessException(
                    "Section of subject '" + section.getSubject().getCode()
                            + "' has ANULADA enrollment records and cannot be deleted"
            );
        }

        sectionRepository.delete(section);
    }

    private Section findSectionOrThrow(Long id) {
        return sectionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Section not found with id: " + id));
    }

    private Subject findSubjectOrThrow(Long subjectId) {
        return subjectRepository.findById(subjectId)
                .orElseThrow(() -> new ResourceNotFoundException("Subject not found with id: " + subjectId));
    }

    private AcademicPeriod findPeriodOrThrow(Long periodId) {
        return academicPeriodRepository.findById(periodId)
                .orElseThrow(() -> new ResourceNotFoundException("Academic period not found with id: " + periodId));
    }

    private Teacher findTeacherOrThrow(Long teacherId) {
        return teacherRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found with id: " + teacherId));
    }

    private void requireOpenPeriod(AcademicPeriod period) {
        if (period.getStatus() != AcademicPeriodStatus.ABIERTO) {
            throw new BusinessException(
                    "Academic period '" + period.getCode() + "' is CERRADO and its sections cannot be modified"
            );
        }
    }

    private void requireActiveTeacher(Teacher teacher) {
        if (teacher.getStatus() != TeacherStatus.ACTIVO) {
            throw new BusinessException(
                    "Teacher '" + teacher.getRun() + "' is INACTIVO and cannot be assigned to a section"
            );
        }
    }

    /**
     * Reglas del horario: la cantidad de bloques es la suma de horas TEL dividida
     * en 2 (cada bloque son 2 horas pedagogicas) y no se admiten bloques repetidos.
     */
    private void requireValidSchedule(Subject subject, List<ScheduleBlock> blocks) {
        int requiredBlocks =
                (subject.getTelTheory() + subject.getTelExercises() + subject.getTelLab()) / HOURS_PER_BLOCK;

        if (requiredBlocks == 0) {
            throw new BusinessException(
                    "Subject '" + subject.getCode() + "' has no TEL hours, so it cannot have schedule blocks"
            );
        }

        if (blocks.size() != requiredBlocks) {
            throw new BusinessException(
                    "Invalid number of schedule blocks: subject '" + subject.getCode() + "' requires "
                            + requiredBlocks + " and " + blocks.size() + " were given"
            );
        }

        Set<String> seen = new HashSet<>();
        for (ScheduleBlock block : blocks) {
            // En POST /{id}/schedule-blocks el body es una lista y @Valid no llega a
            // sus elementos, asi que el dia y el modulo se revisan tambien aqui.
            if (block.getDay() == null) {
                throw new BusinessException("Day is required (L, M, W, J or V)");
            }
            if (block.getModule() == null || block.getModule() < 1 || block.getModule() > MAX_MODULE) {
                throw new BusinessException("Module must be between 1 and " + MAX_MODULE);
            }
            if (!seen.add(blockKey(block))) {
                throw new BusinessException(
                        "Duplicated schedule block: " + block.getDay() + " module " + block.getModule()
                );
            }
        }
    }

    /**
     * Regla: un docente no puede tener dos secciones que coincidan en dia y modulo
     * dentro del mismo periodo academico.
     */
    private void requireTeacherHasNoConflict(Teacher teacher,
                                             AcademicPeriod period,
                                             List<ScheduleBlock> blocks,
                                             Long sectionIdToIgnore) {
        Set<String> wanted = blocks.stream()
                .map(this::blockKey)
                .collect(Collectors.toSet());

        List<Section> teacherSections =
                sectionRepository.findByTeacherIdAndAcademicPeriodId(teacher.getId(), period.getId());

        for (Section other : teacherSections) {
            if (sectionIdToIgnore != null && sectionIdToIgnore.equals(other.getId())) {
                continue;
            }
            for (ScheduleBlock block : other.getScheduleBlocks()) {
                if (wanted.contains(blockKey(block))) {
                    throw new BusinessException(
                            "Teacher '" + teacher.getRun() + "' already has a section on "
                                    + block.getDay() + " module " + block.getModule()
                                    + " in academic period '" + period.getCode() + "'"
                    );
                }
            }
        }
    }

    private String blockKey(ScheduleBlock block) {
        return block.getDay() + "-" + block.getModule();
    }
}
