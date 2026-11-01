package com.example.siga.integration;

import com.example.siga.entity.ScheduleDay;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proceso 3 - Cierre de periodo: tres casos de exito y tres de rechazo sobre
 * POST /api/academic-closure/{periodId}, la unica via por la que un periodo
 * queda CERRADO (PATCH /api/academic-periods/{id}/status?status=CERRADO delega
 * en el mismo servicio).
 *
 * El escenario base deja al estudiante con tres asignaturas inscritas en
 * 2040-1. El plan tiene una cuarta asignatura sin seccion, para que aprobar las
 * tres no lo deje EGRESADO y los contadores del cierre sean predecibles.
 */
@DisplayName("Integracion - Proceso 3: cierre de periodo academico")
class AcademicClosureIntegrationTest extends AbstractIntegrationTest {

    private final Caller teacherCaller = teacher("ana.teacher@siga.cl");
    private Caller studentCaller;

    private long teacherId;
    private long studentId;
    private long periodId;
    private long pendingSubjectId;
    private long mathSectionId;
    private long englishSectionId;

    /** MAT101, FIS101 e ING101, en ese orden. */
    private List<Long> enrollmentIds;

    @BeforeEach
    void setUpGradedPeriod() throws Exception {
        long careerId = createCareer("40001");
        long studyPlanId = createCurrentStudyPlan(careerId, "PLAN-2024");

        periodId = createPeriod(2040, 1);
        teacherId = createTeacher(teacherCaller.email());

        long mathId = createSubject(studyPlanId, "MAT101", 1, 2, 2, 0);
        long physicsId = createSubject(studyPlanId, "FIS101", 1, 2, 2, 0);
        long englishId = createSubject(studyPlanId, "ING101", 1, 2, 2, 0);
        pendingSubjectId = createSubject(studyPlanId, "HIS101", 1, 2, 2, 0);

        mathSectionId = createSection(mathId, periodId, teacherId, 30,
                block(ScheduleDay.L, 1), block(ScheduleDay.M, 1));
        long physicsSectionId = createSection(physicsId, periodId, teacherId, 30,
                block(ScheduleDay.L, 2), block(ScheduleDay.M, 2));
        englishSectionId = createSection(englishId, periodId, teacherId, 30,
                block(ScheduleDay.L, 3), block(ScheduleDay.M, 3));

        studentCaller = student("pedro.student@siga.cl");
        studentId = createStudent(careerId, studentCaller.email());
        matriculate(studentId, periodId);

        enrollmentIds = enroll(studentCaller, periodId, mathSectionId, physicsSectionId, englishSectionId);
    }

    @Test
    @DisplayName("Exito 1: cierre con todas las notas ingresadas")
    void closesPeriodWithEveryGradeRecorded() throws Exception {
        recordGrade(teacherCaller, enrollmentIds.get(0), "5.0");
        recordGrade(teacherCaller, enrollmentIds.get(1), "6.0");
        recordGrade(teacherCaller, enrollmentIds.get(2), "3.0");

        closePeriod(ADMIN, periodId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.academicPeriodId").value((int) periodId))
                .andExpect(jsonPath("$.academicPeriodCode").value("2040-1"))
                .andExpect(jsonPath("$.status").value("CERRADO"))
                .andExpect(jsonPath("$.consolidatedEnrollments").value(3))
                .andExpect(jsonPath("$.approvedCount").value(2))
                .andExpect(jsonPath("$.failedCount").value(1))
                .andExpect(jsonPath("$.graduatedStudentRuns", hasSize(0)))
                .andExpect(jsonPath("$.eliminatedStudentRuns", hasSize(0)));

        assertThat(periodStatus(periodId)).isEqualTo("CERRADO");

        // Una reprobacion de tres asignaturas no cambia el estado del estudiante
        send(get("/api/students/{id}", studentId), ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REGULAR"));
    }

    @Test
    @DisplayName("Exito 2: el cierre genera el historial academico")
    void closureGeneratesAcademicHistory() throws Exception {
        recordGrade(teacherCaller, enrollmentIds.get(0), "5.0");
        recordGrade(teacherCaller, enrollmentIds.get(1), "6.0");
        recordGrade(teacherCaller, enrollmentIds.get(2), "3.0");

        closePeriod(ADMIN, periodId).andExpect(status().isOk());

        // El historial es un snapshot: copia el codigo y el nombre de la asignatura
        send(get("/api/academic-history/student/{studentId}", studentId), ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].studentId", everyItem(is((int) studentId))))
                .andExpect(jsonPath("$[*].academicPeriodCode", everyItem(is("2040-1"))))
                .andExpect(jsonPath("$[*].subjectCode", containsInAnyOrder("MAT101", "FIS101", "ING101")))
                .andExpect(jsonPath("$[?(@.subjectCode == 'MAT101')].result", contains("APROBADO")))
                .andExpect(jsonPath("$[?(@.subjectCode == 'MAT101')].subjectName", contains("Subject MAT101")))
                .andExpect(jsonPath("$[?(@.subjectCode == 'ING101')].result", contains("REPROBADO")));

        send(get("/api/academic-history/me"), studentCaller)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    @DisplayName("Exito 3: el periodo queda CERRADO y rechaza toda escritura")
    void closedPeriodRejectsFurtherChanges() throws Exception {
        long firstGradeId = recordGrade(teacherCaller, enrollmentIds.get(0), "5.0");
        recordGrade(teacherCaller, enrollmentIds.get(1), "6.0");
        recordGrade(teacherCaller, enrollmentIds.get(2), "4.0");

        // PATCH .../status?status=CERRADO delega en el cierre de periodo
        send(patch("/api/academic-periods/{id}/status", periodId).param("status", "CERRADO"), ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("2040-1"))
                .andExpect(jsonPath("$.status").value("CERRADO"));

        assertThat(periodStatus(periodId)).isEqualTo("CERRADO");

        // Un periodo CERRADO no recibe secciones nuevas
        send(post("/api/sections"), ADMIN,
                sectionRequest(pendingSubjectId, periodId, teacherId, 30,
                        block(ScheduleDay.W, 1), block(ScheduleDay.J, 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("CERRADO")));

        // Ni cambios de notas
        send(put("/api/grades/{id}", firstGradeId), teacherCaller,
                gradeRequest(enrollmentIds.get(0), "7.0"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("grades cannot be changed")));

        // Ni inscripciones
        send(post("/api/enrollments"), studentCaller, enrollmentRequest(periodId, mathSectionId))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Rechazo 1: falta una nota")
    void rejectsClosureWithPendingGrades() throws Exception {
        recordGrade(teacherCaller, enrollmentIds.get(0), "5.0");
        recordGrade(teacherCaller, enrollmentIds.get(1), "6.0");

        closePeriod(ADMIN, periodId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message", containsString("pending grades")))
                .andExpect(jsonPath("$.message", containsString("ING101")));

        assertThat(periodStatus(periodId)).isEqualTo("ABIERTO");

        // El acta muestra la fila pendiente que impide el cierre
        send(get("/api/grades/section/{sectionId}", englishSectionId), teacherCaller)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].subjectCode").value("ING101"))
                .andExpect(jsonPath("$[0].value").value(nullValue()))
                .andExpect(jsonPath("$[0].result").value(nullValue()));

        // Y el historial sigue vacio
        send(get("/api/academic-history/student/{studentId}", studentId), ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Rechazo 2: el periodo ya esta CERRADO")
    void rejectsClosingAnAlreadyClosedPeriod() throws Exception {
        recordGrade(teacherCaller, enrollmentIds.get(0), "5.0");
        recordGrade(teacherCaller, enrollmentIds.get(1), "6.0");
        recordGrade(teacherCaller, enrollmentIds.get(2), "3.0");

        closePeriod(ADMIN, periodId).andExpect(status().isOk());

        closePeriod(ADMIN, periodId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message", containsString("already CERRADO and cannot be closed again")));

        // ABIERTO -> CERRADO no tiene retorno
        send(patch("/api/academic-periods/{id}/status", periodId).param("status", "ABIERTO"), ADMIN)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("cannot be reopened")));

        // El cierre no se duplico: el historial sigue con los tres registros
        send(get("/api/academic-history/student/{studentId}", studentId), ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    @DisplayName("Rechazo 3: no se cumplen las condiciones del cierre (rol, token y periodo)")
    void rejectsClosureWithoutTheRequiredConditions() throws Exception {
        recordGrade(teacherCaller, enrollmentIds.get(0), "5.0");
        recordGrade(teacherCaller, enrollmentIds.get(1), "6.0");
        recordGrade(teacherCaller, enrollmentIds.get(2), "3.0");

        // Las notas estan completas, pero el cierre es exclusivo del ADMIN
        closePeriod(teacherCaller, periodId).andExpect(status().isForbidden());
        closePeriod(studentCaller, periodId).andExpect(status().isForbidden());

        // Sin token no hay cierre
        send(post("/api/academic-closure/{periodId}", periodId), null)
                .andExpect(status().isUnauthorized());

        // Y el periodo tiene que existir
        closePeriod(ADMIN, 9_999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));

        assertThat(periodStatus(periodId)).isEqualTo("ABIERTO");

        send(get("/api/academic-history/student/{studentId}", studentId), ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
