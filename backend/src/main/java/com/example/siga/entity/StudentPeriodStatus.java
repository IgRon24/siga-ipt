package com.example.siga.entity;

import jakarta.persistence.*;
import lombok.*;

/** Indica si un estudiante esta matriculado en un periodo academico determinado. */
@Entity
@Table(
        name = "student_period_status",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"student_id", "academic_period_id"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentPeriodStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_period_id", nullable = false)
    private AcademicPeriod academicPeriod;

    @Enumerated(EnumType.STRING)
    @Column(name = "enrollment_status", nullable = false, length = 20)
    private MatriculationStatus enrollmentStatus;
}
