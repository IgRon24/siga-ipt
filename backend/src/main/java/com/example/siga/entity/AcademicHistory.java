package com.example.siga.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Snapshot inmutable del historial academico. Guarda el codigo y nombre de la
 * asignatura copiados al momento del cierre, para que los cambios posteriores en
 * asignaturas o planes no alteren la historia del estudiante.
 *
 * La escritura es parte del cierre de periodo (Epica 7); la Epica 6 solo lo lee
 * para saber que asignaturas estan aprobadas o reprobadas.
 */
@Entity
@Table(name = "academic_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AcademicHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "subject_code", nullable = false, length = 20)
    private String subjectCode; // snapshot

    @Column(name = "subject_name", nullable = false, length = 150)
    private String subjectName; // snapshot

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_period_id", nullable = false)
    private AcademicPeriod academicPeriod;

    @Column(nullable = false, precision = 2, scale = 1)
    private BigDecimal grade; // 1.0 a 7.0

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AcademicHistoryResult result;
}
