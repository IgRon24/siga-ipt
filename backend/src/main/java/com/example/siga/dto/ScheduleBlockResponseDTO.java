package com.example.siga.dto;

import com.example.siga.entity.ScheduleDay;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleBlockResponseDTO {
    private Long id;
    private ScheduleDay day;
    private Integer module;
}
