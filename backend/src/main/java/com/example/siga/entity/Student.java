package com.example.siga.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "student",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"run"}),
                @UniqueConstraint(columnNames = {"email"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 12)
    private String run; // ej: "12345678-9"

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(nullable = false, length = 150)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StudentStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "career_id", nullable = false)
    private Career career;

    // El plan no lo elige el usuario: es el plan VIGENTE de la carrera al momento
    // de registrar al estudiante, y no cambia cuando la carrera publica un plan nuevo.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "study_plan_id", nullable = false)
    private StudyPlan studyPlan;
}
