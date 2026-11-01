package com.example.siga.controller;


import com.example.siga.dto.StudyPlanRequestDTO;
import com.example.siga.dto.StudyPlanResponseDTO;
import com.example.siga.service.StudyPlanService;
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
@RequestMapping("/api/study-plans")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class StudyPlanController {

    private final StudyPlanService studyPlanService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT')")
    @Operation(summary = "List all study plans")
    public ResponseEntity<List<StudyPlanResponseDTO>> findAll() {
        return ResponseEntity.ok(studyPlanService.findAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT')")
    @Operation(summary = "Get a study plan by id")
    public ResponseEntity<StudyPlanResponseDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(studyPlanService.findById(id));
    }

    @GetMapping("/career/{careerId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT')")
    @Operation(summary = "List study plans by career")
    public ResponseEntity<List<StudyPlanResponseDTO>> findByCareer(@PathVariable Long careerId) {
        return ResponseEntity.ok(studyPlanService.findByCareer(careerId));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new study plan (ADMIN only)")
    public ResponseEntity<StudyPlanResponseDTO> create(@Valid @RequestBody StudyPlanRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(studyPlanService.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a study plan (ADMIN only)")
    public ResponseEntity<StudyPlanResponseDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody StudyPlanRequestDTO dto) {
        return ResponseEntity.ok(studyPlanService.update(id, dto));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Change study plan status (ADMIN only)")
    public ResponseEntity<StudyPlanResponseDTO> changeStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        return ResponseEntity.ok(studyPlanService.changeStatus(id, status));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a study plan (ADMIN only)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        studyPlanService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
