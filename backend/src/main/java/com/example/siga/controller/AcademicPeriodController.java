package com.example.siga.controller;

import com.example.siga.dto.AcademicPeriodRequestDTO;
import com.example.siga.dto.AcademicPeriodResponseDTO;
import com.example.siga.dto.ClosureReadinessResponseDTO;
import com.example.siga.service.AcademicClosureService;
import com.example.siga.service.AcademicPeriodService;
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
@RequestMapping("/api/academic-periods")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AcademicPeriodController {

    private final AcademicPeriodService academicPeriodService;
    private final AcademicClosureService academicClosureService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT')")
    @Operation(summary = "List all academic periods, optionally filtered by status")
    public ResponseEntity<List<AcademicPeriodResponseDTO>> findAll(
            @RequestParam(required = false) String status) {
        if (status != null) {
            return ResponseEntity.ok(academicPeriodService.findByStatus(status));
        }
        return ResponseEntity.ok(academicPeriodService.findAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT')")
    @Operation(summary = "Get an academic period by id")
    public ResponseEntity<AcademicPeriodResponseDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(academicPeriodService.findById(id));
    }

    /**
     * Lo que responderia el cierre antes de ejecutarlo: se delega al cierre de
     * periodo, que es el dueno de la regla, igual que PATCH /{id}/status.
     */
    @GetMapping("/{id}/closure-readiness")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Check whether an academic period can be closed and what grades are missing (ADMIN only)")
    public ResponseEntity<ClosureReadinessResponseDTO> closureReadiness(@PathVariable Long id) {
        return ResponseEntity.ok(academicClosureService.readiness(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create an academic period, always ABIERTO (ADMIN only)")
    public ResponseEntity<AcademicPeriodResponseDTO> create(@Valid @RequestBody AcademicPeriodRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(academicPeriodService.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update an academic period while it is ABIERTO (ADMIN only)")
    public ResponseEntity<AcademicPeriodResponseDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody AcademicPeriodRequestDTO dto) {
        return ResponseEntity.ok(academicPeriodService.update(id, dto));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Close an academic period; CERRADO has no return (ADMIN only)")
    public ResponseEntity<AcademicPeriodResponseDTO> changeStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        return ResponseEntity.ok(academicPeriodService.changeStatus(id, status));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete an ABIERTO academic period without sections (ADMIN only)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        academicPeriodService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
