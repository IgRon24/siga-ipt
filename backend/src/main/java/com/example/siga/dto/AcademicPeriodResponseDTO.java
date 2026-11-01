package com.example.siga.dto;

import com.example.siga.entity.AcademicPeriodStatus;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AcademicPeriodResponseDTO {
    private Long id;
    private String code;
    private Integer year;
    private Integer semester;
    private AcademicPeriodStatus status;
}
