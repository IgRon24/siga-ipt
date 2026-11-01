package com.example.siga.controller;

import com.example.siga.dto.CareerRequestDTO;
import com.example.siga.dto.CareerResponseDTO;
import com.example.siga.service.CareerService;
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
@RequestMapping("/api/careers")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class CareerController {

    private final CareerService careerService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT')")
    @Operation(summary = "List all careers")
    public ResponseEntity<List<CareerResponseDTO>> findAll() {
        return ResponseEntity.ok(careerService.findAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT')")
    @Operation(summary = "Get a career by id")
    public ResponseEntity<CareerResponseDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(careerService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new career (ADMIN only)")
    public ResponseEntity<CareerResponseDTO> create(@Valid @RequestBody CareerRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(careerService.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a career (ADMIN only)")
    public ResponseEntity<CareerResponseDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody CareerRequestDTO dto) {
        return ResponseEntity.ok(careerService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a career (ADMIN only)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        careerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
