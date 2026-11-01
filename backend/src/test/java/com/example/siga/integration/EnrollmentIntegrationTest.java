package com.example.siga.integration;

import com.example.siga.entity.ScheduleDay;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proceso 1 - Inscripcion: tres casos de exito y tres de rechazo sobre
 * POST /api/enrollments, que es una operacion por lote y toma al estudiante del
 * email del token.
 *
 * El plan tiene cuatro asignaturas de primer semestre (MAT101, FIS101, ING101 y
 * HIS101) y una de segundo (MAT201) que exige MAT101 como prerrequisito. El
 * periodo 2040-1 ofrece seccion para las cuatro primeras; los escenarios que
 * necesitan historial academico cierran ese periodo y siguen en 2040-2.
 */
@DisplayName("Integracion - Proceso 1: inscripcion academica")
class EnrollmentIntegrationTest extends AbstractIntegrationTest {

    private final Caller teacherCaller = teacher("ana.teacher@siga.cl");
    private Caller studentCaller;

    private long careerId;
    private long teacherId;
    private long studentId;
    private long firstPeriodId;

    private long mathId;
    private long calculusId;

    private long mathSectionId;
    private long physicsSectionId;
    private long englishSectionId;

    @BeforeEach
    void setUpAcademicOffer() throws Exception {
        careerId = createCareer("30001");
        long studyPlanId = createCurrentStudyPlan(careerId, "PLAN-2024");

        firstPeriodId = createPeriod(2040, 1);
        teacherId = createTeacher(teacherCaller.email());

        mathId = createSubject(studyPlanId, "MAT101", 1, 2, 2, 0);
        long physicsId = createSubject(studyPlanId, "FIS101", 1, 2, 2, 0);
        long englishId = createSubject(studyPlanId, "ING101", 1, 2, 2, 0);
        long historyId = createSubject(studyPlanId, "HIS101", 1, 2, 2, 0);
        calculusId = createSubject(studyPlanId, "MAT201", 2, 2, 2, 0);
        addPrerequisite(calculusId, mathId);

        mathSectionId = createSection(mathId, firstPeriodId, teacherId, 30,
                block(ScheduleDay.L, 1), block(ScheduleDay.M, 1));
        physicsSectionId = createSection(physicsId, firstPeriodId, teacherId, 30,
                block(ScheduleDay.L, 2), block(ScheduleDay.M, 2));
        englishSectionId = createSection(englishId, firstPeriodId, teacherId, 30,
                block(ScheduleDay.L, 3), block(ScheduleDay.M, 3));
        createSection(historyId, firstPeriodId, teacherId, 30,
                block(ScheduleDay.L, 4), block(ScheduleDay.M, 4));

        studentCaller = student("pedro.student@siga.cl");
        studentId = createStudent(careerId, studentCaller.email());
        matriculate(studentId, firstPeriodId);
    }

    @Test
    @DisplayName("Exito 1: inscripcion valida de tres asignaturas")
    void enrollsValidSelection() throws Exception {
        send(post("/api/enrollments"), studentCaller,
                enrollmentRequest(firstPeriodId, mathSectionId, physicsSectionId, englishSectionId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].status", everyItem(is("INSCRITA"))))
                .andExpect(jsonPath("$[*].studentId", everyItem(is((int) studentId))))
                .andExpect(jsonPath("$[*].subjectCode", containsInAnyOrder("MAT101", "FIS101", "ING101")));

        send(get("/api/enrollments/me"), studentCaller)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));

        // La oferta disponible deja solo la asignatura que no inscribio
        send(get("/api/enrollments/available").param("academicPeriodId", String.valueOf(firstPeriodId)),
                studentCaller)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].subjectCode").value("HIS101"));
    }

    @Test
    @DisplayName("Exito 2: inscripcion con prerrequisitos cumplidos")
    void enrollsSubjectWithApprovedPrerequisite() throws Exception {
        // El prerrequisito solo queda aprobado cuando el cierre consolida el historial
        List<Long> enrollmentIds = enroll(studentCaller, firstPeriodId,
                mathSectionId, physicsSectionId, englishSectionId);
        for (Long enrollmentId : enrollmentIds) {
            recordGrade(teacherCaller, enrollmentId, "5.0");
        }
        closePeriod(ADMIN, firstPeriodId).andExpect(status().isOk());

        send(get("/api/academic-history/me"), studentCaller)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].result", everyItem(is("APROBADO"))))
                .andExpect(jsonPath("$[*].subjectCode", containsInAnyOrder("MAT101", "FIS101", "ING101")));

        long secondPeriodId = createPeriod(2040, 2);
        long calculusSectionId = createSection(calculusId, secondPeriodId, teacherId, 30,
                block(ScheduleDay.L, 1), block(ScheduleDay.M, 1));
        matriculate(studentId, secondPeriodId);

        // MAT201 exige MAT101, que ahora esta APROBADO en el historial
        send(get("/api/enrollments/available").param("academicPeriodId", String.valueOf(secondPeriodId)),
                studentCaller)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].subjectCode").value("MAT201"));

        send(post("/api/enrollments"), studentCaller,
                enrollmentRequest(secondPeriodId, calculusSectionId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].subjectCode").value("MAT201"))
                .andExpect(jsonPath("$[0].subjectSemester").value(2))
                .andExpect(jsonPath("$[0].status").value("INSCRITA"));
    }

    @Test
    @DisplayName("Exito 3: inscripcion con cupo disponible")
    void enrollsWhileSeatsRemain() throws Exception {
        long secondPeriodId = createPeriod(2040, 2);
        long limitedSectionId = createSection(mathId, secondPeriodId, teacherId, 2,
                block(ScheduleDay.L, 1), block(ScheduleDay.M, 1));
        matriculate(studentId, secondPeriodId);

        Caller otherCaller = student("maria.student@siga.cl");
        long otherStudentId = createStudent(careerId, otherCaller.email());
        matriculate(otherStudentId, secondPeriodId);

        enroll(studentCaller, secondPeriodId, limitedSectionId);

        // Queda un cupo de los dos: el segundo estudiante alcanza
        send(get("/api/enrollments/available").param("academicPeriodId", String.valueOf(secondPeriodId)),
                otherCaller)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].capacity").value(2))
                .andExpect(jsonPath("$[0].enrolled").value(1))
                .andExpect(jsonPath("$[0].availableSeats").value(1));

        send(post("/api/enrollments"), otherCaller, enrollmentRequest(secondPeriodId, limitedSectionId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].subjectCode").value("MAT101"))
                .andExpect(jsonPath("$[0].studentId").value((int) otherStudentId));

        send(get("/api/enrollments/student/{studentId}", otherStudentId), ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("INSCRITA"));
    }

    @Test
    @DisplayName("Rechazo 1: estudiante no REGULAR")
    void rejectsNonRegularStudent() throws Exception {
        send(patch("/api/students/{id}/status", studentId).param("status", "POSTERGACION"), ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("POSTERGACION"));

        send(post("/api/enrollments"), studentCaller,
                enrollmentRequest(firstPeriodId, mathSectionId, physicsSectionId, englishSectionId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message", containsString("POSTERGACION")));

        send(get("/api/enrollments/me"), studentCaller)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Rechazo 2: prerrequisito no aprobado")
    void rejectsSubjectWithoutApprovedPrerequisite() throws Exception {
        // MAT201 se ofrece en el mismo periodo, pero MAT101 recien se esta inscribiendo
        long calculusSectionId = createSection(calculusId, firstPeriodId, teacherId, 30,
                block(ScheduleDay.W, 1), block(ScheduleDay.J, 1));

        send(post("/api/enrollments"), studentCaller,
                enrollmentRequest(firstPeriodId, mathSectionId, physicsSectionId, calculusSectionId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message",
                        containsString("requires prerequisite 'MAT101', which is not APROBADO")));

        // El lote completo se rechaza: no queda ninguna inscripcion
        send(get("/api/enrollments/me"), studentCaller)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Rechazo 3: cupo agotado")
    void rejectsSectionWithoutSeats() throws Exception {
        long secondPeriodId = createPeriod(2040, 2);
        long fullSectionId = createSection(mathId, secondPeriodId, teacherId, 1,
                block(ScheduleDay.L, 1), block(ScheduleDay.M, 1));
        matriculate(studentId, secondPeriodId);

        Caller otherCaller = student("maria.student@siga.cl");
        long otherStudentId = createStudent(careerId, otherCaller.email());
        matriculate(otherStudentId, secondPeriodId);

        enroll(studentCaller, secondPeriodId, fullSectionId);

        send(get("/api/enrollments/available").param("academicPeriodId", String.valueOf(secondPeriodId)),
                otherCaller)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].availableSeats").value(0));

        send(post("/api/enrollments"), otherCaller, enrollmentRequest(secondPeriodId, fullSectionId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message", containsString("has no available seats (capacity 1)")));

        send(get("/api/enrollments/student/{studentId}", otherStudentId), ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
