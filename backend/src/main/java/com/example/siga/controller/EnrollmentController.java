package com.example.siga.controller;

import com.example.siga.dto.AvailableSectionResponseDTO;
import com.example.siga.dto.EnrollmentRequestDTO;
import com.example.siga.dto.EnrollmentResponseDTO;
import com.example.siga.service.EnrollmentService;
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
@RequestMapping("/api/enrollments")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;
    private final StudentService studentService;

    /**
     * Oferta disponible para el estudiante en el periodo. El STUDENT consulta la
     * propia (se identifica por el email del token) y el ADMIN puede pasar studentId.
     */
    @GetMapping("/available")
    @PreAuthorize("hasAnyRole('ADMIN', 'STUDENT')")
    @Operation(summary = "List the subjects the student can enroll in an academic period")
    public ResponseEntity<List<AvailableSectionResponseDTO>> findAvailable(
            @RequestParam Long academicPeriodId,
            @RequestParam(required = false) Long studentId,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(
                enrollmentService.findAvailable(resolveStudentId(studentId, jwt), academicPeriodId));
    }

    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Enroll the selected sections for the authenticated student")
    public ResponseEntity<List<EnrollmentResponseDTO>> enroll(
            @Valid @RequestBody EnrollmentRequestDTO dto,
            @AuthenticationPrincipal Jwt jwt) {
        Long studentId = currentStudentId(jwt);
        return ResponseEntity.status(HttpStatus.CREATED).body(enrollmentService.enroll(studentId, dto));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "List the authenticated student's own enrollments")
    public ResponseEntity<List<EnrollmentResponseDTO>> findMine(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(enrollmentService.findByStudent(currentStudentId(jwt)));
    }

    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List a student's enrollments (ADMIN only)")
    public ResponseEntity<List<EnrollmentResponseDTO>> findByStudent(@PathVariable Long studentId) {
        return ResponseEntity.ok(enrollmentService.findByStudent(studentId));
    }

    /** Anulacion: solo el propio estudiante y con el periodo ABIERTO. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Cancel one of the authenticated student's enrollments")
    public ResponseEntity<Void> cancel(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        enrollmentService.cancel(currentStudentId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    private Long resolveStudentId(Long studentId, Jwt jwt) {
        return studentId != null ? studentId : currentStudentId(jwt);
    }

    private Long currentStudentId(Jwt jwt) {
        return studentService.findByEmail(jwt.getClaimAsString("email")).getId();
    }
}
