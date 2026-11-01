package com.example.siga.controller;

import com.example.siga.dto.AcademicHistoryResponseDTO;
import com.example.siga.service.AcademicHistoryService;
import com.example.siga.service.StudentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Solo lectura: el historial lo escribe el cierre de periodo, nunca un endpoint. */
@RestController
@RequestMapping("/api/academic-history")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AcademicHistoryController {

    private final AcademicHistoryService academicHistoryService;
    private final StudentService studentService;

    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "The authenticated student's own academic history")
    public ResponseEntity<List<AcademicHistoryResponseDTO>> findMine(@AuthenticationPrincipal Jwt jwt) {
        Long studentId = studentService.findByEmail(jwt.getClaimAsString("email")).getId();
        return ResponseEntity.ok(academicHistoryService.findByStudent(studentId));
    }

    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    @Operation(summary = "A student's academic history")
    public ResponseEntity<List<AcademicHistoryResponseDTO>> findByStudent(@PathVariable Long studentId) {
        return ResponseEntity.ok(academicHistoryService.findByStudent(studentId));
    }
}
