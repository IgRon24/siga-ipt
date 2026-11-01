package com.example.siga.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(
        name = "section",
        uniqueConstraints = {
                // Regla: una asignatura tiene una unica seccion por periodo academico
                @UniqueConstraint(columnNames = {"subject_id", "academic_period_id"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Section {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_period_id", nullable = false)
    private AcademicPeriod academicPeriod;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher teacher;

    @Column(nullable = false)
    private Integer capacity; // 1 a 60

    @OneToMany(
            mappedBy = "section",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @Builder.Default
    private List<ScheduleBlock> scheduleBlocks = new ArrayList<>();

    /**
     * Reemplaza el horario completo manteniendo las dos puntas de la relacion.
     *
     * Los bloques que se repiten se conservan tal cual en vez de borrarse y volver a
     * insertarse: Hibernate escribe los INSERT antes de los DELETE del mismo flush, y
     * reinsertar un dia+modulo que ya existe choca con el unique (section_id, day, module).
     */
    public void replaceScheduleBlocks(List<ScheduleBlock> blocks) {
        Set<String> incoming = blocks.stream()
                .map(Section::blockKey)
                .collect(Collectors.toSet());

        scheduleBlocks.removeIf(existing -> !incoming.contains(blockKey(existing)));

        Set<String> kept = scheduleBlocks.stream()
                .map(Section::blockKey)
                .collect(Collectors.toSet());

        blocks.stream()
                .filter(block -> !kept.contains(blockKey(block)))
                .forEach(block -> {
                    block.setSection(this);
                    scheduleBlocks.add(block);
                });
    }

    private static String blockKey(ScheduleBlock block) {
        return block.getDay() + "-" + block.getModule();
    }
}
