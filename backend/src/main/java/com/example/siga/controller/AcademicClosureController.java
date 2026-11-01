package com.example.siga.controller;

import com.example.siga.dto.AcademicClosureResponseDTO;
import com.example.siga.service.AcademicClosureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cierre de semestre: consolida el historial academico y deja el periodo CERRADO.
 * Es equivalente a PATCH /api/academic-periods/{id}/status?status=CERRADO, que
 * delega aqui, pero devuelve el resumen de lo consolidado.
 */
@RestController
@RequestMapping("/api/academic-closure")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AcademicClosureController {

    private final AcademicClosureService academicClosureService;

    @PostMapping("/{periodId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Close an academic period and consolidate the academic history (ADMIN only)")
    public ResponseEntity<AcademicClosureResponseDTO> close(@PathVariable Long periodId) {
        return ResponseEntity.ok(academicClosureService.close(periodId));
    }
}
