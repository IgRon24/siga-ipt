package com.example.siga.dto;

import com.example.siga.entity.StudyPlanStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudyPlanRequestDTO {

    @NotBlank(message = "Identifier is required")
    @Size(max = 20, message = "Identifier must not exceed 20 characters")
    private String identifier;

    @NotBlank(message = "Name is required")
    @Size(max = 150, message = "Name must not exceed 150 characters")
    private String name;

    @NotNull(message = "Status is required")
    private StudyPlanStatus status;

    @NotNull(message = "Career id is required")
    private Long careerId;
}
