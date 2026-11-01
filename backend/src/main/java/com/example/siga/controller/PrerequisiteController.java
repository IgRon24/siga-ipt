package com.example.siga.controller;

import com.example.siga.dto.PrerequisiteRequestDTO;
import com.example.siga.dto.PrerequisiteResponseDTO;
import com.example.siga.service.PrerequisiteService;
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
@RequestMapping("/api/subjects/{subjectId}/prerequisites")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class PrerequisiteController {

    private final PrerequisiteService prerequisiteService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT')")
    @Operation(summary = "List the prerequisites of a subject")
    public ResponseEntity<List<PrerequisiteResponseDTO>> findBySubject(@PathVariable Long subjectId) {
        return ResponseEntity.ok(prerequisiteService.findBySubject(subjectId));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Add a prerequisite to a subject (ADMIN only)")
    public ResponseEntity<PrerequisiteResponseDTO> add(
            @PathVariable Long subjectId,
            @Valid @RequestBody PrerequisiteRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(prerequisiteService.add(subjectId, dto));
    }

    @DeleteMapping("/{prerequisiteSubjectId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Remove a prerequisite from a subject (ADMIN only)")
    public ResponseEntity<Void> remove(
            @PathVariable Long subjectId,
            @PathVariable Long prerequisiteSubjectId) {
        prerequisiteService.remove(subjectId, prerequisiteSubjectId);
        return ResponseEntity.noContent().build();
    }
}
