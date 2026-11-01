package com.example.siga.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "subject",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"study_plan_id", "code"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String code; // ej: "TCS101"

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false)
    private Integer semester; // 1 a 4

    @Column(name = "tel_theory", nullable = false)
    private Integer telTheory;

    @Column(name = "tel_exercises", nullable = false)
    private Integer telExercises;

    @Column(name = "tel_lab", nullable = false)
    private Integer telLab;

    @Column(nullable = false)
    private Integer sct; // 1 a 7

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "study_plan_id", nullable = false)
    private StudyPlan studyPlan;
}
