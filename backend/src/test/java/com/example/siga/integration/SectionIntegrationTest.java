package com.example.siga.integration;

import com.example.siga.entity.ScheduleDay;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proceso 2 - Seccion: tres casos de exito y tres de rechazo sobre
 * POST /api/sections y POST /api/sections/{id}/schedule-blocks.
 *
 * La oferta academica se arma sobre un periodo ABIERTO, exige un docente ACTIVO
 * al momento de la asignacion y un horario de (T+E+L)/2 bloques sin choques con
 * las otras secciones del mismo docente en el periodo.
 */
@DisplayName("Integracion - Proceso 2: oferta academica (secciones)")
class SectionIntegrationTest extends AbstractIntegrationTest {

    private long periodId;
    private long activeTeacherId;
    private long secondTeacherId;
    private long inactiveTeacherId;

    /** MAT101 y ING101 suman 4 horas TEL: 2 bloques. FIS101 suma 6: 3 bloques. */
    private long twoBlockSubjectId;
    private long otherTwoBlockSubjectId;
    private long threeBlockSubjectId;

    @BeforeEach
    void setUpAcademicOffer() throws Exception {
        long careerId = createCareer("20001");
        long studyPlanId = createCurrentStudyPlan(careerId, "PLAN-2024");

        periodId = createPeriod(2040, 1);

        activeTeacherId = createTeacher("ana.active@siga.cl");
        secondTeacherId = createTeacher("luis.second@siga.cl");
        inactiveTeacherId = createTeacher("rosa.inactive@siga.cl");
        deactivateTeacher(inactiveTeacherId);

        twoBlockSubjectId = createSubject(studyPlanId, "MAT101", 1, 2, 2, 0);
        otherTwoBlockSubjectId = createSubject(studyPlanId, "ING101", 1, 2, 2, 0);
        threeBlockSubjectId = createSubject(studyPlanId, "FIS101", 1, 2, 2, 2);
    }

    @Test
    @DisplayName("Exito 1: seccion valida")
    void createsValidSection() throws Exception {
        long sectionId = idOf(send(post("/api/sections"), ADMIN,
                sectionRequest(twoBlockSubjectId, periodId, activeTeacherId, 30,
                        block(ScheduleDay.L, 1), block(ScheduleDay.M, 1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subjectCode").value("MAT101"))
                .andExpect(jsonPath("$.academicPeriodCode").value("2040-1"))
                .andExpect(jsonPath("$.capacity").value(30))
                .andExpect(jsonPath("$.scheduleBlocks", hasSize(2))));

        // La seccion queda publicada en la oferta del periodo
        send(get("/api/sections/{id}", sectionId), ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjectCode").value("MAT101"))
                .andExpect(jsonPath("$.teacherId").value((int) activeTeacherId));

        send(get("/api/sections").param("academicPeriodId", String.valueOf(periodId)), ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value((int) sectionId));
    }

    @Test
    @DisplayName("Exito 2: docente ACTIVO asignado como responsable")
    void assignsActiveTeacherAsResponsible() throws Exception {
        long sectionId = createSection(otherTwoBlockSubjectId, periodId, secondTeacherId, 25,
                block(ScheduleDay.W, 2), block(ScheduleDay.J, 2));

        send(get("/api/sections").param("teacherId", String.valueOf(secondTeacherId)), ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value((int) sectionId))
                .andExpect(jsonPath("$[0].teacherId").value((int) secondTeacherId))
                .andExpect(jsonPath("$[0].teacherFullName").value("Teacher luis.second"));
    }

    @Test
    @DisplayName("Exito 3: horario valido, el reemplazo deja solo los bloques nuevos")
    void replacesScheduleWithValidBlocks() throws Exception {
        long sectionId = createSection(threeBlockSubjectId, periodId, activeTeacherId, 30,
                block(ScheduleDay.L, 3), block(ScheduleDay.M, 3), block(ScheduleDay.W, 3));

        // POST /schedule-blocks reemplaza el horario completo, no agrega bloques
        send(post("/api/sections/{id}/schedule-blocks", sectionId), ADMIN,
                List.of(block(ScheduleDay.J, 4), block(ScheduleDay.V, 4), block(ScheduleDay.L, 5)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleBlocks", hasSize(3)))
                .andExpect(jsonPath("$.scheduleBlocks[*].day", containsInAnyOrder("J", "V", "L")))
                .andExpect(jsonPath("$.scheduleBlocks[*].module", containsInAnyOrder(4, 4, 5)));
    }

    @Test
    @DisplayName("Rechazo 1: docente INACTIVO")
    void rejectsInactiveTeacher() throws Exception {
        send(post("/api/sections"), ADMIN,
                sectionRequest(twoBlockSubjectId, periodId, inactiveTeacherId, 30,
                        block(ScheduleDay.L, 1), block(ScheduleDay.M, 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message", containsString("INACTIVO")));

        send(get("/api/sections").param("academicPeriodId", String.valueOf(periodId)), ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Rechazo 2: conflicto de horario del docente en el periodo")
    void rejectsTeacherScheduleConflict() throws Exception {
        createSection(twoBlockSubjectId, periodId, activeTeacherId, 30,
                block(ScheduleDay.L, 1), block(ScheduleDay.M, 1));

        // Mismo docente y periodo, con el lunes modulo 1 ya ocupado
        send(post("/api/sections"), ADMIN,
                sectionRequest(otherTwoBlockSubjectId, periodId, activeTeacherId, 30,
                        block(ScheduleDay.L, 1), block(ScheduleDay.J, 2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message", containsString("already has a section on L module 1")));

        send(get("/api/sections").param("academicPeriodId", String.valueOf(periodId)), ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("Rechazo 3: cantidad incorrecta de bloques para las horas TEL")
    void rejectsWrongNumberOfScheduleBlocks() throws Exception {
        // MAT101 son 4 horas TEL: exige 2 bloques y se envian 3
        send(post("/api/sections"), ADMIN,
                sectionRequest(twoBlockSubjectId, periodId, activeTeacherId, 30,
                        block(ScheduleDay.L, 1), block(ScheduleDay.M, 1), block(ScheduleDay.W, 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message", containsString("requires 2 and 3 were given")));

        // Y el mismo rechazo al reemplazar el horario de una seccion ya creada
        long sectionId = createSection(twoBlockSubjectId, periodId, activeTeacherId, 30,
                block(ScheduleDay.L, 1), block(ScheduleDay.M, 1));

        send(post("/api/sections/{id}/schedule-blocks", sectionId), ADMIN,
                List.of(block(ScheduleDay.W, 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("requires 2 and 1 were given")));

        send(get("/api/sections/{id}", sectionId), ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleBlocks", hasSize(2)));
    }
}
