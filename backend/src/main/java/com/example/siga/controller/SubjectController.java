package com.example.siga.controller;

import com.example.siga.dto.SubjectRequestDTO;
import com.example.siga.dto.SubjectResponseDTO;
import com.example.siga.service.SubjectService;
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
@RequestMapping("/api/subjects")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class SubjectController {

    private final SubjectService subjectService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT')")
    @Operation(summary = "List all subjects")
    public ResponseEntity<List<SubjectResponseDTO>> findAll() {
        return ResponseEntity.ok(subjectService.findAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT')")
    @Operation(summary = "Get a subject by id")
    public ResponseEntity<SubjectResponseDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(subjectService.findById(id));
    }

    @GetMapping("/study-plan/{studyPlanId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT')")
    @Operation(summary = "List subjects by study plan")
    public ResponseEntity<List<SubjectResponseDTO>> findByStudyPlan(@PathVariable Long studyPlanId) {
        return ResponseEntity.ok(subjectService.findByStudyPlan(studyPlanId));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new subject (ADMIN only)")
    public ResponseEntity<SubjectResponseDTO> create(@Valid @RequestBody SubjectRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(subjectService.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a subject (ADMIN only)")
    public ResponseEntity<SubjectResponseDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody SubjectRequestDTO dto) {
        return ResponseEntity.ok(subjectService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a subject (ADMIN only)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        subjectService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
