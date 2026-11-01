package com.example.siga.controller;

import com.example.siga.dto.ScheduleBlockRequestDTO;
import com.example.siga.dto.SectionRequestDTO;
import com.example.siga.dto.SectionResponseDTO;
import com.example.siga.service.SectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sections")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class SectionController {

    private final SectionService sectionService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT')")
    @Operation(summary = "List sections, optionally filtered by academic period or teacher")
    public ResponseEntity<List<SectionResponseDTO>> findAll(
            @RequestParam(required = false) Long academicPeriodId,
            @RequestParam(required = false) Long teacherId) {
        if (academicPeriodId != null) {
            return ResponseEntity.ok(sectionService.findByAcademicPeriod(academicPeriodId));
        }
        if (teacherId != null) {
            return ResponseEntity.ok(sectionService.findByTeacher(teacherId));
        }
        return ResponseEntity.ok(sectionService.findAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT')")
    @Operation(summary = "Get a section by id")
    public ResponseEntity<SectionResponseDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(sectionService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a section with its full schedule (ADMIN only)")
    public ResponseEntity<SectionResponseDTO> create(@Valid @RequestBody SectionRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sectionService.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a section and its schedule (ADMIN only)")
    public ResponseEntity<SectionResponseDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody SectionRequestDTO dto) {
        return ResponseEntity.ok(sectionService.update(id, dto));
    }

    /** Reemplaza el horario completo: la cantidad de bloques debe seguir cuadrando con el TEL. */
    @PostMapping("/{id}/schedule-blocks")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Replace the whole schedule of a section (ADMIN only)")
    public ResponseEntity<SectionResponseDTO> replaceSchedule(
            @PathVariable Long id,
            @Valid @RequestBody List<ScheduleBlockRequestDTO> blocks) {
        return ResponseEntity.ok(sectionService.replaceSchedule(id, blocks));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a section (ADMIN only)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        sectionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
