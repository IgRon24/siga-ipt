Proyecto: SIGA IPT – Sistema Integrado de Gestión Académica

Modalidad: Individual

Tipo: Aplicación web monolítica por capas

Frontend: ReactJS + Vite

Backend: Java + Spring Boot

Base de datos: MySQL o PostgreSQL

Autenticación: Keycloak + Spring Security + OAuth2 Resource Server

Despliegue: Docker Compose + Nginx + 3 réplicas backend + 1 frontend + DB con volumen

CI/CD: GitHub Actions + Docker Hub

Repositorio: Monorepo GitHub

Código: Inglés

## 1. Objetivo

Desarrollar y desplegar una aplicación web monolítica basada en arquitectura por capas que centralice la gestión académica del

Instituto Profesional de Tecnología (IPT), cubriendo las 7 épicas del negocio, autenticación con roles, pruebas unitarias e integración, y despliegue en producción con Docker Compose.

## 2. Alcance y restricciones

- Proyecto individual.

- No se entrega informe escrito.

- Evaluación presencial en fecha/hora asignada.

- Frontend único.

- Backend único monolítico por capas (Controller, Service, Repository, Entity).

- No se implementa autenticación propia ni se almacenan contraseñas.

- Toda credencial se maneja por variables de entorno.

- Código fuente en inglés.

- API REST documentada con OpenAPI/Swagger.

- Base de datos relacional con Spring Data JPA.

- Frontend con React funcional, Hooks, Axios, React Router y librería de UI.

- Despliegue manual con Docker Compose.

- Pipeline CD con GitHub Actions que ejecute pruebas, construya y publique imágenes Docker solo si las pruebas pasan.


## 3. Stack tecnológico

| Capa | Tecnología |
| --- | --- |
| Frontend | ReactJS + Vite + Axios + React Router + MUI/Bootstrap/Tailwind |
| Backend Java + Spring Boot |   |
| Persistencia | Spring Data JPA + MySQL/PostgreSQL |
| Seguridad | Spring Security + OAuth2 Resource Server + Keycloak |
| Documentación OpenAPI / Swagger |   |
| Utilidades Lombok |   |
| Pruebas | JUnit, Mockito, Spring Boot Test, Testcontainers si aplica |
| Contenedores | Docker + Docker Compose |
| Balanceo Nginx |   |
| CI/CD | GitHub Actions + Docker Hub |
| Nube | AWS, Azure, GCP, DigitalOcean u otro |

## 4. Arquitectura objetivo

## 4.1 Backend por capas

com.ipt.siga

├── config

├── security

├── controller

├── service

│ └── impl

├── repository

├── entity

├── dto

├── mapper

├── exception

└── SigaIptApplication

## Responsabilidades:

- Controller: expone endpoints REST, recibe DTOs, delega en Service.

- Service: contiene TODA la lógica de negocio y validaciones.

- Repository: acceso a datos con Spring Data JPA.

- Entity: modelo de dominio.

- DTO/Mapper: separación entre API y entidades.

- Exception: manejo global uniforme de errores.

- Security: configuración OAuth2/JWT y roles.


## 4.2 Frontend

src

├── api

├── components

├── pages

├── routes

├── hooks

├── services

├── contexts

├── layouts

└── utils

Requisitos:

- Componentes funcionales y Hooks.

- Axios para comunicación con backend.

- React Router para navegación.

- Rutas protegidas por rol.

- Interfaz con MUI, Bootstrap, Tailwind u otra librería.

- Token JWT adjunto en cada request.

## 4.3 Infraestructura

/

├── backend/

├── frontend/

├── nginx/

├── keycloak/

├── docker-compose.yml

├── .github/workflows/

└── README.md

Componentes en Docker Compose:

- 1 instancia de base de datos MySQL/PostgreSQL.

- 3 réplicas del backend.

- 1 Nginx como balanceador de carga hacia las 3 réplicas.

- 1 única instancia del frontend.

- Volumen Docker para persistencia de DB.

- Red Docker interna.

- Variables de entorno para configuración.

- Imágenes frontend/backend en Docker Hub.


| Entidad Student Career StudyPlan | Campos clave RUN, fullName, email, status, career, studyPlan N:1 Career, N:1 StudyPlan code, name, description, status, 1:N StudyPlan durationSemesters id, code, name, status, career N:1 Career, 1:N Subject code, name, semester, telTheory, telExercises, | Relaciones |
| --- | --- | --- |
| Subject Prerequisite Teacher | N:1 StudyPlan, N:M Prerequisite telLab, sct, studyPlan subject, prerequisiteSubject N:1 Subject RUN, fullName, email, professionalTitle, 1:N Section academicDegree, status |   |
| AcademicPeriod StudentPeriodStatus | code, year, semester, status student, academicPeriod, enrollmentStatus N:1 Student, N:1 AcademicPeriod | 1:N Section, 1:N StudentPeriodStatus |
| Section | id, subject, academicPeriod, teacher, capacity, | N:1 Subject, N:1 AcademicPeriod, N:1 Teacher, |
| ScheduleBlock | scheduleBlocks 1:N ScheduleBlock day, module, section N:1 Section |   |
| Enrollment | student, subject, section, academicPeriod, date, N:1 Student, N:1 Section status |   |
| Grade AcademicHistory | enrollment, finalGrade, result 1:1 Enrollment student, subjectCode, subjectName, N:1 Student academicPeriod, grade, result |   |

## 5.1 Entidades clave con lógica especial

## StudentPeriodStatus

Representa si el estudiante está matriculado en un período. No es un módulo completo de matrícula; solo el dato que el

sistema consume.

```
StudentPeriodStatus
-------------------
id
student
academicPeriod
enrollmentStatus: MATRICULATED | NOT_MATRICULATED
```

## AcademicHistory como snapshot inmutable

Guarda información histórica directamente, sin depender de Subject :


```
AcademicHistory
---------------
id
student
subjectCode ← snapshot
subjectName ← snapshot
academicPeriod
grade
result
```

Esto garantiza que modificaciones posteriores en asignaturas o planes no alteren la historia académica.

## Flujo Enrollment → Grade → AcademicHistory

```
Student
↓
Enrollment (período ABIERTO)
↓
Grade (docente registra nota final)
↓
Period Closure (admin cierra)
↓
AcademicHistory (snapshot inmutable)
↓
Student status updates (ELIMINADO / EGRESADO)
```

## 5.2 Estados y transiciones válidas

## Student:

```
REGULAR → POSTERGACIÓN
REGULAR → RETIRO TEMPORAL
REGULAR → EGRESADO (automático)
REGULAR → ELIMINADO (automático)
```

- EGRESADO y ELIMINADO no se asignan manualmente.

- ELIMINADO no vuelve a REGULAR.

Career: ACTIVA ↔ INACTIVA

StudyPlan: VIGENTE ↔ NO VIGENTE

Teacher: ACTIVO ↔ INACTIVO (solo si no tiene secciones en período ABIERTO)

AcademicPeriod: ABIERTO → CERRADO (sin retorno)

Grade result: APROBADO / REPROBADO (automático según nota)


## 6. Matriz de reglas de negocio

| Regla Backend RUN único (Student) Email único (Student) Student REGULAR para enrollment Student matriculado en período | Frontend — — | Test |
| --- | --- | --- |
| Prerrequisitos aprobados | — |   |
| Capacidad de sección Conflicto de horario | — |   |
| Docente ACTIVO para sección | — |   |
| Período ABIERTO |   |   |
| Todas las notas ingresadas para cierre Segunda reprobación → ELIMINADO | — — |   |
| Plan completo → EGRESADO | — |   |
| Bloques = TEL / 2 | — |   |
| Código carrera único Código asignatura único por plan | — — |   |
| SCT entre 1 y 7 |   |   |
| Suma TEL < 8 |   |   |
| Prerrequisitos de semestres anteriores No eliminar entidades con historial | — — |   |

## 7. Módulos funcionales

## Módulo 0 – Seguridad y autenticación

Objetivo: Integrar Keycloak, validar JWT y restringir endpoints por rol.

## Reglas:

- Roles mínimos: ADMIN, TEACHER, STUDENT.

- Frontend restringe vistas según rol.

- Backend valida token JWT con Spring Security.

- No se almacenan contraseñas en la DB de la aplicación.

- No se implementa login propio.

## Endpoints:

- GET /api/me – información del usuario autenticado y roles.


## Configuración:

- Spring Security OAuth2 Resource Server con issuer-uri / jwk-set-uri .

- @PreAuthorize("hasRole('ADMIN')") en endpoints.

Roles: todos.

## Módulo 1 – Gestión de estudiantes (Épica 1)

## Reglas clave:

- Campos obligatorios: RUN, nombre completo, email, carrera.

- RUN y email únicos.

- Asociar al plan de estudios vigente de la carrera.

- Estado inicial: REGULAR.

- Estados: REGULAR, POSTERGACIÓN, RETIRO TEMPORAL, EGRESADO, ELIMINADO.

- EGRESADO y ELIMINADO son automáticos, no manuales.

- No eliminar físicamente si tiene inscripciones o historial.

- Carrera/plan solo modificables si no tiene inscripciones ni historial.

- ADMIN puede asignar/modificar REGULAR, POSTERGACIÓN, RETIRO TEMPORAL; REGULAR inicial es automático.

## Endpoints:

- GET /api/students

- POST /api/students

- GET /api/students/{id}

- PUT /api/students/{id}

- PATCH /api/students/{id}/status

- DELETE /api/students/{id} solo si no tiene historial/inscripciones.

Roles: ADMIN principal; STUDENT consulta su propia información.

## Módulo 2 – Carreras y planes de estudio (Épica 2)

## Reglas clave:

- Código de carrera único.

- Carrera: código, nombre, descripción, estado, duración 4 semestres.

- Una carrera puede tener varios planes, pero solo uno VIGENTE.

- Nuevo plan vigente deja al anterior NO VIGENTE.

- Plan asociado a carrera existente e identificador propio.

- No eliminar plan con estudiantes.

- No eliminar carrera con estudiantes o planes; solo cambiar estado.

- Carrera INACTIVA no recibe nuevos estudiantes.

- Crear nuevo plan no modifica plan de estudiantes anteriores.

## Endpoints:

- GET/POST/PUT /api/careers

- GET/POST/PUT /api/study-plans


- PATCH /api/study-plans/{id}/status

Roles: ADMIN.

## Módulo 3 – Asignaturas y prerrequisitos (Épica 3)

## Reglas clave:

- Código único dentro del plan.

- Semestre entre 1 y 4.

- SCT > 0 y <= 7.

- Horas TEL: Teoría, Ejercicios, Laboratorio >= 0.

- Suma TEL < 8 horas semanales.

- Prerrequisitos: 0 a 3, mismo plan, semestres anteriores.

- No eliminar asignatura si está en historial académico.

- Modificaciones no alteran historial previo.

## Endpoints:

- GET/POST/PUT /api/subjects

- GET/POST/DELETE /api/subjects/{id}/prerequisites

Roles: ADMIN.

## Módulo 4 – Gestión de docentes (Épica 4)

## Reglas clave:

- RUN y email únicos.

- Campos: RUN, nombre, email, título, grado, estado.

- Estado inicial: ACTIVO.

- Solo ACTIVO puede ser asignado a nuevas secciones.

- No eliminar si tiene secciones.

- No cambiar a INACTIVO si tiene secciones en período ABIERTO.

- Cambio de estado no modifica histórico.

## Endpoints:

- GET/POST/PUT /api/teachers

- PATCH /api/teachers/{id}/status

Roles: ADMIN.

## Módulo 5 – Oferta académica y secciones (Épica 5)

## Reglas clave:

- Período: año-semestre, estado ABIERTO/CERRADO.

- Período inicia ABIERTO.

- Sección: asignatura + período, única por asignatura/período.


- Docente responsable ACTIVO.

- Capacidad > 0 y <= 60.

- Inscritos no superan capacidad.

- Al menos un bloque horario.

- Días válidos: L, M, W, J, V.

- Módulos: 1 a 6.

- Cantidad de bloques = (TEL teoría + TEL ejercicios + TEL laboratorio) / 2.

- No bloques duplicados.

- Docente no puede tener choque de horario.

- No eliminar sección con inscritos.

## Validación automática de bloques:

```
int requiredBlocks =
(subject.getTelTheory()
+ subject.getTelExercises()
+ subject.getTelLab()) / 2;
if (scheduleBlocks.size() != requiredBlocks) {
throw new BusinessException("Invalid number of schedule blocks");
}
```

## Endpoints:

- GET/POST/PUT /api/academic-periods

- PATCH /api/academic-periods/{id}/status

- GET/POST/PUT /api/sections

- POST /api/sections/{id}/schedule-blocks

Roles: ADMIN; TEACHER consulta sus secciones.

## Módulo 6 – Inscripción académica (Épica 6)

## Reglas clave:

- Estudiante debe estar REGULAR y matriculado (StudentPeriodStatus).

- Solo asignaturas de su plan ofrecidas en el período.

- Inscripción/anulación solo con período ABIERTO.

- No reinscribir asignatura aprobada.

- Reprobada una vez sí puede reinscribirse.

- Prerrequisitos aprobados.

- No inscribir misma asignatura más de una vez por período.

- Mínimo 3, máximo 6 asignaturas.

- Si quedan menos de 3, puede inscribir pendientes disponibles.

- No inscribir semestre superior con pendientes inferiores disponibles.

- Cupos disponibles.

- Sin conflicto de horario.

- Registro: estudiante, asignatura, sección, período, fecha.

- Anulación libera cupo.

## Endpoints:


- GET /api/enrollments/available

- POST /api/enrollments

- DELETE /api/enrollments/{id} anulación

- GET /api/enrollments/student/{id}

Roles: STUDENT; ADMIN consulta.

## Módulo 7 – Calificaciones y cierre de semestre (Épica 7)

## Reglas clave:

- Solo docente responsable registra calificaciones de sus secciones.

- Solo estudiantes inscritos.

- Una calificación final por inscripción.

- Rango 1.0 a 7.0.

- Modificable mientras período ABIERTO.

- = 4.0 APROBADO; < 4.0 REPROBADO.

- Cierre solo en período ABIERTO.

- Todas las inscripciones deben tener calificación; si faltan, rechazar e informar secciones.

- Durante cierre se consolida historial académico (snapshot).

- Si reprueba misma asignatura por segunda vez: ELIMINADO.

- Si aprueba todas: EGRESADO.

- Período pasa a CERRADO.

- Período CERRADO no permite modificar calificaciones, inscribir, anular ni cerrar de nuevo.

## Endpoints:

- GET /api/grades/section/{sectionId}

- POST /api/grades

- PUT /api/grades/{id}

- POST /api/academic-closure/{periodId}

Roles: TEACHER; ADMIN ejecuta cierre.

## 8. Roles y permisos

| Rol | Permisos principales |
| --- | --- |
| ADMIN | Administrar estudiantes, docentes, carreras, planes, asignaturas, períodos, oferta, secciones. Consultar |
| inscripciones. Ejecutar cierre. |   |
| TEACHER | Consultar secciones asignadas y estudiantes inscritos. Registrar/modificar calificaciones mientras período |
| ABIERTO. |   |
| STUDENT | Consultar información académica, plan, oferta, historial. Realizar y consultar inscripciones en períodos |
| habilitados. |   |


## 9. Seguridad con Keycloak

- Keycloak gestiona usuarios, roles y credenciales.

- Frontend obtiene token JWT.

- Backend valida JWT con Spring Security OAuth2 Resource Server.

- Roles extraídos del token.

- Endpoints protegidos con @PreAuthorize .

- No se guardan contraseñas en la DB de la aplicación.

- Variables de entorno para issuer-uri , client-id , etc.

## Orden de integración:

- 1. Keycloak local con realm, client y roles ADMIN/TEACHER/STUDENT.

- 2. GET /api/me funcional.

- 3. Autorización por rol en endpoints.

- 4. Frontend con login y token.

## 10. Frontend: vistas y rutas

| Ruta | Vista Rol |   |
| --- | --- | --- |
| /login | Login Keycloak Público |   |
| /dashboard | Inicio según rol Todos |   |
| /students /careers /study-plans /subjects /teachers /periods /sections | Gestión estudiantes ADMIN Gestión carreras ADMIN Gestión planes ADMIN Gestión asignaturas ADMIN Gestión docentes ADMIN Gestión períodos ADMIN Gestión secciones ADMIN |   |
| /enrollments /my-sections /grades | Inscripción STUDENT Mis secciones TEACHER Registro calificaciones TEACHER |   |
| /closure /my-history | Cierre período ADMIN Historial académico STUDENT |   |

Desarrollo incremental: a medida que se completa cada módulo backend, se construye la página correspondiente.


- Métodos: GET, POST, PUT, PATCH, DELETE.

- Códigos: 200, 201, 204, 400, 401, 403, 404, 409, 422, 500.

- DTOs para request/response.

- Validación con Bean Validation.

- Manejo global de excepciones.

- Documentación Swagger/OpenAPI.

## Formato de error uniforme:

```
{
"timestamp": "2026-10-08T10:30:00",
"status": 409,
"error": "BUSINESS_RULE_VIOLATION",
"message": "Student cannot enroll because the subject capacity is full",
"path": "/api/enrollments"
}
```

## 12. Estrategia de testing

## 12.1 Pruebas unitarias

- Capa Service.

- Cobertura de líneas >= 90%.

- JUnit + Mockito.

- Casos felices y de error.

- Ejecutar durante el desarrollo, no al final.

## 12.2 Pruebas de integración

3 procesos relevantes × 3 casos exitosos + 3 rechazo = 18 casos mínimos.

Proceso 1 – Enrollment

Éxito:

- 1. Enrollment válido.

- 2. Enrollment con prerrequisitos cumplidos.

- 3. Enrollment con cupo disponible.

Rechazo:

- 1. Student no REGULAR.

- 2. Prerrequisito no aprobado.

- 3. Cupo agotado.

Proceso 2 – Section

Éxito:

- 1. Sección válida.


- 2. Docente ACTIVO.

- 3. Horario válido.

## Rechazo:

- 1. Docente INACTIVO.

- 2. Conflicto de horario.

- 3. Cantidad incorrecta de bloques.

## Proceso 3 – Period Closure

## Éxito:

- 1. Todas las notas ingresadas.

- 2. Historia generada.

- 3. Período cerrado correctamente.

## Rechazo:

- 1. Falta una nota.

- 2. Se intenta cerrar un período ya cerrado.

- 3. Existen condiciones que impiden el cierre.

Herramientas: @SpringBootTest , MockMvc, Testcontainers si aplica.

## 13. CI/CD con GitHub Actions

Pipeline mínimo:

- 1. Checkout del código.

- 2. Configurar JDK.

- 3. Ejecutar pruebas unitarias backend.

- 4. Ejecutar pruebas de integración backend.

- 5. Construir imágenes Docker frontend y backend.

- 6. Publicar imágenes en Docker Hub solo si las pruebas pasan.

Secrets requeridos:

- DOCKERHUB_USERNAME

- DOCKERHUB_TOKEN

## 14. Despliegue con Docker Compose

| Servicio | Descripción |
| --- | --- |
| db | MySQL/PostgreSQL con volumen |
| backend | 3 réplicas, imagen Docker Hub |
| nginx | Balanceador hacia backend |
| frontend | 1 instancia, imagen Docker Hub |


- Red Docker interna.

- Volumen para DB.

- Variables de entorno para credenciales.

- Nginx escucha puerto 80/443.

- Backend accesible solo por red interna.

- Frontend accesible desde navegador.

- Health checks opcionales ( /actuator/health ).

## Estrategia de prueba temprana:

- 1. Primero: backend + frontend + database.

- 2. Después: agregar Nginx.

- 3. Finalmente: 3 réplicas backend.

## 15. Datos iniciales (seed)

## Basados en el Apéndice A:

- 3 carreras: 10450, 10451, 10452.

- 3 planes de estudio.

- Asignaturas con TEL, SCT y prerrequisitos.

- Docentes de prueba.

- Estudiantes en distintos estados: REGULAR, POSTERGACIÓN, ELIMINADO.

- Períodos: 2026-1 OPEN, 2025-2 CLOSED.

- Secciones de ejemplo.

## Usuarios Keycloak:

- ADMIN

- TEACHER

- STUDENT_REGULAR

- STUDENT_POSTERGATION

- STUDENT_ELIMINATED

## 16. Plan de fases

## F0 – Base técnica

- Repo monorepo GitHub.

- React + Vite.

- Spring Boot + dependencias.

- DB local.

- Docker Compose inicial.

- Keycloak base.

Entregable: Frontend → Backend → DB funcionando con Keycloak.


## F1 – Arquitectura backend + seguridad + Swagger

- Entidades, repositorios, DTOs, mappers.

- Excepciones globales.

- Configuración Spring Security + OAuth2.

- GET /api/me .

- Swagger.

Entregable: Backend base con seguridad y documentación.

## F2 – Épicas 1–4

- Students, Careers, StudyPlans, Subjects, Prerequisites, Teachers.

- Cada módulo: Entity, Repository, Service, Controller, Validation, Unit tests.

- Frontend correspondiente por módulo.

Entregable: Módulos administrativos funcionando y testeados.

## F3 – Épica 5: Oferta académica

- AcademicPeriod, Section, ScheduleBlock.

- Validación de bloques TEL.

- Conflictos de docente y horario.

- Capacidad.

- Unit tests + integration tests.

Entregable: Oferta académica operativa.

## F4 – Épica 6: Inscripción

- StudentPeriodStatus.

- Enrollment.

- Validaciones: REGULAR, matriculado, plan, prerrequisitos, cupos, horarios, 3–6 asignaturas.

- Unit tests + integration tests (6 casos).

Entregable: Inscripción funcional.

## F5 – Épica 7: Calificaciones y cierre

- Grade.

- AcademicHistory como snapshot.

- Cierre de período.

- Reglas: APROBADO/REPROBADO, ELIMINADO, EGRESADO.

- Unit tests + integration tests (6 casos).

Entregable: Cierre de semestre operativo.


## F6 – Frontend completo

- Pages, componentes, rutas, guards por rol.

- Formularios, tablas, manejo de errores.

- Dashboards por rol.

- Integración completa con backend.

Entregable: Frontend integrado y navegable.

## F7 – Testing final

- Cobertura >= 90%.

- 18 escenarios de integración completos.

- Regression testing.

- API tests, security tests, validation tests.

Entregable: Calidad validada.

## F8 – CI/CD

- GitHub Actions: checkout → unit tests → integration tests → build → Docker images → Docker Hub.

- Publicación solo si las pruebas pasan.

Entregable: Pipeline funcional.

## F9 – Despliegue en producción

- Docker Compose completo.

- Nginx + 3 réplicas backend.

- Volumen DB.

- Variables de entorno.

- Despliegue en nube.

- Accesible desde navegador.

Entregable: Aplicación en producción.

## F10 – Revisión final

- Checklist contra rúbrica.

- Prueba completa del sistema.

- Ensayo de presentación.

Entregable: Entrega final lista.

## 17. Checklist de cumplimiento

- Monorepo GitHub con frontend y backend.


- Backend Spring Boot por capas.

- Frontend React + Vite + Hooks.

- Axios y React Router.

- Librería UI (MUI/Bootstrap/Tailwind).

- Keycloak con roles ADMIN, TEACHER, STUDENT.

- Spring Security + OAuth2 Resource Server.

- Spring Data JPA + DB relacional.

- OpenAPI/Swagger.

- StudentPeriodStatus implementado.

- AcademicHistory como snapshot inmutable.

- Flujo Enrollment → Grade → AcademicHistory.

- Validación automática de bloques TEL.

- Dockerfiles frontend/backend.

- Docker Compose con DB, 3 backend, Nginx, frontend, volumen.

- Variables de entorno, sin secretos en código.

- GitHub Actions: tests, build, push.

- Pruebas unitarias Service >= 90%.

- Pruebas integración 18 casos.

- Despliegue en nube accesible.

- Código en inglés.

## 18. Riesgos y mitigación

| Riesgo | Mitigación |
| --- | --- |
| Complejidad de reglas de inscripción | Implementar validaciones por servicio y tests tempranos |
| Configuración Keycloak | Documentar realm, client, roles y probar con Postman |
| Balanceo Nginx + réplicas | Usar Docker Compose con red interna y resolver DNS |
| Cobertura 90% | Escribir tests junto con cada servicio |
| Tiempo individual | Priorizar épicas críticas y automatizar despliegue |
| Persistencia DB | Usar volumen Docker y variables de entorno |
| Cambios en asignaturas afectan historial | Snapshot en AcademicHistory |
| Matrícula no modelada | StudentPeriodStatus |
