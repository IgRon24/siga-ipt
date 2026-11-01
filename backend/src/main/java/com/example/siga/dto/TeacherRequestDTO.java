package com.example.siga.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeacherRequestDTO {

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

    @NotBlank(message = "Professional title is required")
    @Size(max = 150, message = "Professional title must not exceed 150 characters")
    private String professionalTitle;

    @NotBlank(message = "Academic degree is required")
    @Size(max = 100, message = "Academic degree must not exceed 100 characters")
    private String academicDegree;

    // El estado no se recibe: todo docente queda ACTIVO al registrarse y
    // luego se modifica con PATCH /api/teachers/{id}/status
}
