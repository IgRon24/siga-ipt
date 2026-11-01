package com.example.siga.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "academic_period",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"code"}),
                @UniqueConstraint(columnNames = {"academic_year", "semester"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AcademicPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nomenclatura "2026-1": lo arma el servicio a partir de year y semester. */
    @Column(nullable = false, length = 10)
    private String code;

    // "year" es palabra clave en varios motores, por eso la columna va renombrada.
    @Column(name = "academic_year", nullable = false)
    private Integer year;

    @Column(nullable = false)
    private Integer semester; // 1 o 2

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AcademicPeriodStatus status;
}
