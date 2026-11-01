package com.example.siga.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentRequestDTO {

    @NotBlank(message = "RUN is required")
    @Pattern(regexp = "^\\d{7,8}-[\\dkK]$", message = "RUN must have the format 12345678-9")
    private String run;

    @NotBlank(message = "Full name is required")
    @Size(max = 150, message = "Full name must not exceed 150 characters")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid address")
    @Size(max = 150, message = "Email must not exceed 150 characters")
    private String email;

    @NotNull(message = "Career id is required")
    private Long careerId;

    // El plan de estudios no se recibe: el servicio asigna el plan VIGENTE de la carrera.
    // El estado tampoco: todo estudiante queda REGULAR al registrarse y luego se
    // modifica con PATCH /api/students/{id}/status
}
