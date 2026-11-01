package com.example.siga.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Calificacion final de una inscripcion. Hay una sola por inscripcion y solo se
 * puede registrar o modificar mientras el periodo esta ABIERTO; al cerrar el
 * periodo su valor queda copiado en el historial academico.
 */
@Entity
@Table(
        name = "grade",
        uniqueConstraints = {
                // Regla: una unica calificacion final por inscripcion
                @UniqueConstraint(columnNames = {"enrollment_id"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Grade {

    /** Regla: 4.0 o mas es APROBADO; menos de 4.0 es REPROBADO. */
    public static final BigDecimal PASSING_GRADE = new BigDecimal("4.0");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enrollment_id", nullable = false)
    private Enrollment enrollment;

    // "value" es palabra reservada en varios motores, por eso la columna va renombrada.
    @Column(name = "grade_value", nullable = false, precision = 2, scale = 1)
    private BigDecimal value; // 1.0 a 7.0

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Resultado derivado del valor: no se almacena para no tener dos fuentes de verdad. */
    @Transient
    public AcademicHistoryResult getResult() {
        return value.compareTo(PASSING_GRADE) >= 0
                ? AcademicHistoryResult.APROBADO
                : AcademicHistoryResult.REPROBADO;
    }
}
