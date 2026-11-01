package com.example.siga.controller;

import com.example.siga.dto.StudentRequestDTO;
import com.example.siga.dto.StudentResponseDTO;
import com.example.siga.service.StudentService;
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
@RequestMapping("/api/students")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class StudentController {

    private final StudentService studentService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    @Operation(summary = "List all students, optionally filtered by status or career")
    public ResponseEntity<List<StudentResponseDTO>> findAll(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long careerId) {
        if (careerId != null) {
            return ResponseEntity.ok(studentService.findByCareer(careerId));
        }
        if (status != null) {
            return ResponseEntity.ok(studentService.findByStatus(status));
        }
        return ResponseEntity.ok(studentService.findAll());
    }

    /** El estudiante consulta su propia informacion: se identifica por el email del token. */
    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT')")
    @Operation(summary = "Get the authenticated student's own record")
    public ResponseEntity<StudentResponseDTO> findMe(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(studentService.findByEmail(jwt.getClaimAsString("email")));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    @Operation(summary = "Get a student by id")
    public ResponseEntity<StudentResponseDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(studentService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Register a new student, always REGULAR and on the career's VIGENTE plan (ADMIN only)")
    public ResponseEntity<StudentResponseDTO> create(@Valid @RequestBody StudentRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(studentService.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a student, status not included (ADMIN only)")
    public ResponseEntity<StudentResponseDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody StudentRequestDTO dto) {
        return ResponseEntity.ok(studentService.update(id, dto));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Change student status: REGULAR, POSTERGACION or RETIRO_TEMPORAL (ADMIN only)")
    public ResponseEntity<StudentResponseDTO> changeStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        return ResponseEntity.ok(studentService.changeStatus(id, status));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a student (ADMIN only)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        studentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
