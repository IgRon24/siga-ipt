package com.example.siga.dto;

import com.example.siga.entity.CareerStatus;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CareerResponseDTO {

    private Long id;
    private String code;
    private String name;
    private String description;
    private CareerStatus status;
    private Integer durationSemesters;
}
