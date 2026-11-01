package com.example.siga.controller;

import com.example.siga.dto.GradeRequestDTO;
import com.example.siga.dto.GradeResponseDTO;
import com.example.siga.service.GradeService;
import com.example.siga.service.TeacherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Las calificaciones las registra el docente responsable de la seccion, que se
 * identifica por el email del token y nunca por el cuerpo de la peticion. El
 * ADMIN solo consulta las actas; el cierre del periodo va en su propio endpoint.
 */
@RestController
@RequestMapping("/api/grades")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class GradeController {

    private final GradeService gradeService;
    private final TeacherService teacherService;

    @GetMapping("/section/{sectionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    @Operation(summary = "Grade sheet of a section, including the enrollments without a grade yet")
    public ResponseEntity<List<GradeResponseDTO>> findBySection(
            @PathVariable Long sectionId,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        return ResponseEntity.ok(
                gradeService.findBySection(responsibleTeacherId(jwt, authentication), sectionId));
    }

    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Record the final grade of one enrollment (responsible TEACHER only)")
    public ResponseEntity<GradeResponseDTO> create(
            @Valid @RequestBody GradeRequestDTO dto,
            @AuthenticationPrincipal Jwt jwt) {
        Long teacherId = currentTeacherId(jwt);
        return ResponseEntity.status(HttpStatus.CREATED).body(gradeService.create(teacherId, dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Update a final grade while the academic period is ABIERTO (responsible TEACHER only)")
    public ResponseEntity<GradeResponseDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody GradeRequestDTO dto,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(gradeService.update(currentTeacherId(jwt), id, dto));
    }

    /** El ADMIN consulta cualquier acta, por eso va sin docente responsable. */
    private Long responsibleTeacherId(Jwt jwt, Authentication authentication) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
        return isAdmin ? null : currentTeacherId(jwt);
    }

    private Long currentTeacherId(Jwt jwt) {
        return teacherService.findByEmail(jwt.getClaimAsString("email")).getId();
    }
}
