package com.example.siga.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "teacher",
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
public class Teacher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 12)
    private String run; // ej: "12345678-9"

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(name = "professional_title", nullable = false, length = 150)
    private String professionalTitle;

    @Column(name = "academic_degree", nullable = false, length = 100)
    private String academicDegree;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TeacherStatus status;
}
