package com.example.siga.controller;

import com.example.siga.dto.TeacherRequestDTO;
import com.example.siga.dto.TeacherResponseDTO;
import com.example.siga.service.TeacherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teachers")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class TeacherController {

    private final TeacherService teacherService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT')")
    @Operation(summary = "List all teachers, optionally filtered by status")
    public ResponseEntity<List<TeacherResponseDTO>> findAll(
            @RequestParam(required = false) String status) {
        if (status != null) {
            return ResponseEntity.ok(teacherService.findByStatus(status));
        }
        return ResponseEntity.ok(teacherService.findAll());
    }

    /** El docente consulta su propia ficha: se identifica por el email del token. */
    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT')")
    @Operation(summary = "Get the authenticated teacher's own record")
    public ResponseEntity<TeacherResponseDTO> findMe(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(teacherService.findByEmail(jwt.getClaimAsString("email")));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT')")
    @Operation(summary = "Get a teacher by id")
    public ResponseEntity<TeacherResponseDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(teacherService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new teacher, always ACTIVO (ADMIN only)")
    public ResponseEntity<TeacherResponseDTO> create(@Valid @RequestBody TeacherRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(teacherService.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a teacher, status not included (ADMIN only)")
    public ResponseEntity<TeacherResponseDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody TeacherRequestDTO dto) {
        return ResponseEntity.ok(teacherService.update(id, dto));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Change teacher status (ADMIN only)")
    public ResponseEntity<TeacherResponseDTO> changeStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        return ResponseEntity.ok(teacherService.changeStatus(id, status));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a teacher (ADMIN only)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        teacherService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
