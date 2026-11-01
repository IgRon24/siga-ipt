package com.example.siga.integration;

import com.example.siga.TestcontainersConfiguration;
import com.example.siga.dto.AcademicPeriodRequestDTO;
import com.example.siga.dto.CareerRequestDTO;
import com.example.siga.dto.EnrollmentRequestDTO;
import com.example.siga.dto.GradeRequestDTO;
import com.example.siga.dto.PrerequisiteRequestDTO;
import com.example.siga.dto.ScheduleBlockRequestDTO;
import com.example.siga.dto.SectionRequestDTO;
import com.example.siga.dto.StudentPeriodStatusRequestDTO;
import com.example.siga.dto.StudentRequestDTO;
import com.example.siga.dto.StudyPlanRequestDTO;
import com.example.siga.dto.SubjectRequestDTO;
import com.example.siga.dto.TeacherRequestDTO;
import com.example.siga.entity.CareerStatus;
import com.example.siga.entity.MatriculationStatus;
import com.example.siga.entity.ScheduleDay;
import com.example.siga.entity.StudyPlanStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base de las pruebas de integracion de los tres procesos relevantes
 * (inscripcion, oferta academica y cierre de periodo).
 *
 * Levanta el contexto completo contra el Postgres de Testcontainers y arma los
 * datos de cada escenario llamando a la propia API por HTTP, de modo que lo que
 * se prueba es la cadena controller -> service -> repository con la seguridad y
 * el manejo de errores reales, no los servicios en aislamiento (eso ya lo
 * cubren los *ServiceImplTest con Mockito).
 *
 * Keycloak no corre en las pruebas, asi que el unico colaborador simulado es el
 * JwtDecoder: el token es el Base64 de "rol email" y de ahi se arma el JWT con
 * el claim realm_access que SecurityConfig mapea a ROLE_* y el claim email con
 * que los controladores resuelven al estudiante o al docente.
 */
@SpringBootTest(properties = "spring.jpa.show-sql=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
abstract class AbstractIntegrationTest {

    /** Los RUN son unicos en el sistema, asi que se generan de una secuencia. */
    private static final AtomicInteger RUN_SEQUENCE = new AtomicInteger(10_000_000);

    protected static final Caller ADMIN = new Caller("ADMIN", "admin@siga.cl");

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    /** Quien llama: el rol viaja en realm_access y el email identifica a la persona. */
    protected record Caller(String role, String email) {

        /**
         * El resolver de Spring Security solo acepta ciertos caracteres en el token y
         * el email lleva una arroba: el par rol/email viaja codificado en Base64.
         */
        String bearer() {
            return "Bearer " + Base64.getEncoder()
                    .encodeToString((role + " " + email).getBytes(StandardCharsets.UTF_8));
        }
    }

    protected static Caller teacher(String email) {
        return new Caller("TEACHER", email);
    }

    protected static Caller student(String email) {
        return new Caller("STUDENT", email);
    }

    @BeforeEach
    void prepareTokensAndDatabase() {
        when(jwtDecoder.decode(anyString())).thenAnswer(invocation -> {
            String token = invocation.getArgument(0);
            String[] parts = new String(Base64.getDecoder().decode(token), StandardCharsets.UTF_8).split(" ", 2);
            Instant now = Instant.now();
            return Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject(parts[1])
                    .claim("email", parts[1])
                    .claim("realm_access", Map.of("roles", List.of(parts[0])))
                    .issuedAt(now)
                    .expiresAt(now.plusSeconds(300))
                    .build();
        });

        // Cada escenario parte de una base vacia: el esquema lo crea Hibernate una
        // sola vez (ddl-auto=update) y aqui solo se vacian las filas.
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public'", String.class);
        if (!tables.isEmpty()) {
            jdbcTemplate.execute("TRUNCATE TABLE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE");
        }
    }

    // ---------------------------------------------------------------
    // Llamadas HTTP
    // ---------------------------------------------------------------

    /** Ejecuta la peticion con el token del llamador; un llamador nulo va sin token. */
    protected ResultActions send(MockHttpServletRequestBuilder request, Caller caller, Object body)
            throws Exception {
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        }
        if (caller != null) {
            request.header(HttpHeaders.AUTHORIZATION, caller.bearer());
        }
        return mockMvc.perform(request);
    }

    protected ResultActions send(MockHttpServletRequestBuilder request, Caller caller) throws Exception {
        return send(request, caller, null);
    }

    protected long idOf(ResultActions actions) throws Exception {
        return ((Number) JsonPath.read(bodyOf(actions), "$.id")).longValue();
    }

    protected String bodyOf(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
    }

    // ---------------------------------------------------------------
    // Datos de los escenarios, armados por la API
    // ---------------------------------------------------------------

    protected long createCareer(String code) throws Exception {
        return idOf(send(post("/api/careers"), ADMIN, CareerRequestDTO.builder()
                .code(code)
                .name("Career " + code)
                .description("Career used by the integration tests")
                .status(CareerStatus.ACTIVA)
                .build())
                .andExpect(status().isCreated()));
    }

    /** El estudiante siempre queda asociado al plan VIGENTE de su carrera. */
    protected long createCurrentStudyPlan(long careerId, String identifier) throws Exception {
        return idOf(send(post("/api/study-plans"), ADMIN, StudyPlanRequestDTO.builder()
                .identifier(identifier)
                .name("Study plan " + identifier)
                .status(StudyPlanStatus.VIGENTE)
                .careerId(careerId)
                .build())
                .andExpect(status().isCreated()));
    }

    /** Las horas TEL definen cuantos bloques exige la seccion: la suma dividida en 2. */
    protected long createSubject(long studyPlanId, String code, int semester,
                                 int telTheory, int telExercises, int telLab) throws Exception {
        return idOf(send(post("/api/subjects"), ADMIN, SubjectRequestDTO.builder()
                .code(code)
                .name("Subject " + code)
                .semester(semester)
                .telTheory(telTheory)
                .telExercises(telExercises)
                .telLab(telLab)
                .sct(5)
                .studyPlanId(studyPlanId)
                .build())
                .andExpect(status().isCreated()));
    }

    protected void addPrerequisite(long subjectId, long prerequisiteSubjectId) throws Exception {
        send(post("/api/subjects/{subjectId}/prerequisites", subjectId), ADMIN,
                PrerequisiteRequestDTO.builder().prerequisiteSubjectId(prerequisiteSubjectId).build())
                .andExpect(status().isCreated());
    }

    protected long createTeacher(String email) throws Exception {
        return idOf(send(post("/api/teachers"), ADMIN, TeacherRequestDTO.builder()
                .run(nextRun())
                .fullName("Teacher " + localPart(email))
                .email(email)
                .professionalTitle("Ingeniero Civil Informatico")
                .academicDegree("Magister en Informatica")
                .build())
                .andExpect(status().isCreated()));
    }

    protected void deactivateTeacher(long teacherId) throws Exception {
        send(patch("/api/teachers/{id}/status", teacherId).param("status", "INACTIVO"), ADMIN)
                .andExpect(status().isOk());
    }

    /** Todo periodo nace ABIERTO, con codigo anio-semestre. */
    protected long createPeriod(int year, int semester) throws Exception {
        return idOf(send(post("/api/academic-periods"), ADMIN, AcademicPeriodRequestDTO.builder()
                .year(year)
                .semester(semester)
                .build())
                .andExpect(status().isCreated()));
    }

    protected long createStudent(long careerId, String email) throws Exception {
        return idOf(send(post("/api/students"), ADMIN, StudentRequestDTO.builder()
                .run(nextRun())
                .fullName("Student " + localPart(email))
                .email(email)
                .careerId(careerId)
                .build())
                .andExpect(status().isCreated()));
    }

    /** Sin matricula MATRICULATED en el periodo no hay inscripcion posible. */
    protected void matriculate(long studentId, long academicPeriodId) throws Exception {
        send(put("/api/student-period-status"), ADMIN, StudentPeriodStatusRequestDTO.builder()
                .studentId(studentId)
                .academicPeriodId(academicPeriodId)
                .enrollmentStatus(MatriculationStatus.MATRICULATED)
                .build())
                .andExpect(status().isOk());
    }

    protected ScheduleBlockRequestDTO block(ScheduleDay day, int module) {
        return ScheduleBlockRequestDTO.builder().day(day).module(module).build();
    }

    protected SectionRequestDTO sectionRequest(long subjectId, long academicPeriodId, long teacherId,
                                               int capacity, ScheduleBlockRequestDTO... blocks) {
        return SectionRequestDTO.builder()
                .subjectId(subjectId)
                .academicPeriodId(academicPeriodId)
                .teacherId(teacherId)
                .capacity(capacity)
                .scheduleBlocks(List.of(blocks))
                .build();
    }

    protected long createSection(long subjectId, long academicPeriodId, long teacherId,
                                 int capacity, ScheduleBlockRequestDTO... blocks) throws Exception {
        return idOf(send(post("/api/sections"), ADMIN,
                sectionRequest(subjectId, academicPeriodId, teacherId, capacity, blocks))
                .andExpect(status().isCreated()));
    }

    protected EnrollmentRequestDTO enrollmentRequest(long academicPeriodId, Long... sectionIds) {
        return EnrollmentRequestDTO.builder()
                .academicPeriodId(academicPeriodId)
                .sectionIds(List.of(sectionIds))
                .build();
    }

    /**
     * Inscripcion por lote: el estudiante nunca viaja en el cuerpo, sale del email
     * del token. Devuelve los ids de las inscripciones en el orden pedido.
     */
    protected List<Long> enroll(Caller student, long academicPeriodId, Long... sectionIds) throws Exception {
        String body = bodyOf(send(post("/api/enrollments"), student,
                enrollmentRequest(academicPeriodId, sectionIds))
                .andExpect(status().isCreated()));

        List<Number> ids = JsonPath.read(body, "$[*].id");
        return ids.stream().map(Number::longValue).toList();
    }

    protected GradeRequestDTO gradeRequest(long enrollmentId, String value) {
        return GradeRequestDTO.builder()
                .enrollmentId(enrollmentId)
                .value(new BigDecimal(value))
                .build();
    }

    /** La nota la registra el docente responsable de la seccion. */
    protected long recordGrade(Caller teacher, long enrollmentId, String value) throws Exception {
        return idOf(send(post("/api/grades"), teacher, gradeRequest(enrollmentId, value))
                .andExpect(status().isCreated()));
    }

    protected ResultActions closePeriod(Caller caller, long academicPeriodId) throws Exception {
        return send(post("/api/academic-closure/{periodId}", academicPeriodId), caller);
    }

    protected String periodStatus(long academicPeriodId) throws Exception {
        return JsonPath.read(
                bodyOf(send(get("/api/academic-periods/{id}", academicPeriodId), ADMIN)
                        .andExpect(status().isOk())),
                "$.status");
    }

    protected static String nextRun() {
        return RUN_SEQUENCE.incrementAndGet() + "-0";
    }

    private static String localPart(String email) {
        return email.substring(0, email.indexOf('@'));
    }
}
