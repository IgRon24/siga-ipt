package com.example.siga.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "schedule_block",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"section_id", "day", "module"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "day", nullable = false, length = 1)
    private ScheduleDay day;

    @Column(name = "module", nullable = false)
    private Integer module; // 1 a 6

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false)
    private Section section;
}
