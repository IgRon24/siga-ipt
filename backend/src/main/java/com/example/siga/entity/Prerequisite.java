package com.example.siga.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "prerequisite",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"subject_id", "prerequisite_subject_id"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Prerequisite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Asignatura que exige el prerrequisito. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    /** Asignatura que debe estar aprobada previamente. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prerequisite_subject_id", nullable = false)
    private Subject prerequisiteSubject;
}
