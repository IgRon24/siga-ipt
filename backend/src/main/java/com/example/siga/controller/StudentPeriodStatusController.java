package com.example.siga.controller;

import com.example.siga.dto.StudentPeriodStatusRequestDTO;
import com.example.siga.dto.StudentPeriodStatusResponseDTO;
import com.example.siga.service.StudentPeriodStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * El proceso administrativo de matricula esta fuera del alcance de SIGA: este
 * recurso solo deja registrar y consultar el dato que la inscripcion necesita.
 */
@RestController
@RequestMapping("/api/student-period-status")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class StudentPeriodStatusController {

    private final StudentPeriodStatusService studentPeriodStatusService;

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Register or update a student's matriculation in an academic period (ADMIN only)")
    public ResponseEntity<StudentPeriodStatusResponseDTO> save(
            @Valid @RequestBody StudentPeriodStatusRequestDTO dto) {
        return ResponseEntity.ok(studentPeriodStatusService.save(dto));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT')")
    @Operation(summary = "Get a student's matriculation for an academic period")
    public ResponseEntity<StudentPeriodStatusResponseDTO> findByStudentAndPeriod(
            @RequestParam Long studentId,
            @RequestParam Long academicPeriodId) {
        return ResponseEntity.ok(
                studentPeriodStatusService.findByStudentAndPeriod(studentId, academicPeriodId));
    }

    @GetMapping("/period/{academicPeriodId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    @Operation(summary = "List the matriculation records of an academic period")
    public ResponseEntity<List<StudentPeriodStatusResponseDTO>> findByPeriod(
            @PathVariable Long academicPeriodId) {
        return ResponseEntity.ok(studentPeriodStatusService.findByPeriod(academicPeriodId));
    }

    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    @Operation(summary = "List a student's matriculation records")
    public ResponseEntity<List<StudentPeriodStatusResponseDTO>> findByStudent(@PathVariable Long studentId) {
        return ResponseEntity.ok(studentPeriodStatusService.findByStudent(studentId));
    }
}
