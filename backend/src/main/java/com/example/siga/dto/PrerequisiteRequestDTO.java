package com.example.siga.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrerequisiteRequestDTO {

    @NotNull(message = "Prerequisite subject id is required")
    private Long prerequisiteSubjectId;
}
