# SIGA — Sistema Integrado de Gestión Académica

API REST y aplicación web para la gestión académica de un instituto profesional
técnico: carreras, planes de estudio, asignaturas con prerrequisitos, docentes,
estudiantes, oferta académica por período, inscripción, calificaciones y cierre
de semestre con consolidación del historial.

| Capa | Stack |
| --- | --- |
| Backend | Spring Boot 3.4 · Java 21 · Spring Data JPA · OAuth2 Resource Server |
| Frontend | React 19 · Vite · MUI · React Router · Axios |
| Datos | PostgreSQL |
| Identidad | Keycloak (roles `ADMIN`, `TEACHER`, `STUDENT`) |
| Infraestructura | Docker Compose · Nginx como balanceador |

## Estructura

```
backend/      API Spring Boot (+ http/*.ps1, scripts de prueba manual)
frontend/     SPA React + Vite
nginx/        nginx del borde: balancea /api y publica el sistema
keycloak/     import del realm (ver keycloak/README.md)
perf/         pruebas de carga: poblar usuarios + k6 (ver perf/README.md)
docker-compose.yml          db, Keycloak, 2 réplicas de backend, 2 de frontend y nginx
docker-compose.three.yml    agrega la 3.ª réplica del backend (la que pide la rúbrica)
```

## Levantar con Docker (todo el sistema)

```bash
cp .env.example .env          # y cambiar las credenciales
# dejar el export del realm en keycloak/import/ (ver keycloak/README.md)
docker compose up -d --build
```

| URL | Qué es |
| --- | --- |
| http://localhost | La aplicación |
| http://localhost/auth/admin | Consola de Keycloak |
| http://localhost/swagger-ui.html | La API documentada |
| http://localhost/actuator/health | Health check (balanceado) |

El único servicio con puerto publicado es **nginx**: la base de datos y las
réplicas del backend solo existen en la red interna de Docker. El frontend, la
API y Keycloak quedan bajo un mismo origen, así que no hay CORS que configurar.

Para comprobar el balanceo, apagar una réplica y seguir usando el sistema:

```bash
docker compose stop backend-2      # o frontend-1, para el SPA
docker compose logs -f nginx       # $upstream_addr muestra quién atendió
```

Medido: 20 cargas del SPA se repartieron entre las dos réplicas del frontend, y
con `frontend-1` apagado las siguientes siguieron respondiendo 200.

### Cuántas réplicas del backend

El compose principal levanta **dos** (~640 MB de JVM cada una). El plan de
proyecto pide **tres**: la tercera está en `docker-compose.three.yml`, que la
agrega y cambia la lista que balancea nginx.

```bash
docker compose up -d                                             # dos réplicas
docker compose -f docker-compose.yml -f docker-compose.three.yml up -d   # tres
```

Del **frontend** hay **dos** réplicas (`frontend-1` y `frontend-2`), balanceadas
igual que la API: cada una es nginx sirviendo el build estático, ~7 MB y nada de
CPU, porque el navegador lo descarga una vez y desde ahí todo va a `/api`. El
plan de proyecto pide una única instancia del frontend; están las dos para que
el balanceo del SPA también se pueda demostrar. Lo que pesa son las JVM del
backend.

El número de réplicas vive en un archivo aparte (`nginx/upstream-2.conf` y
`nginx/upstream-3.conf`, que se montan como `00-upstream.conf`) justamente para
que cambiarlo no implique tocar el resto de la configuración de nginx.

## Datos de demostración

Una base recién creada queda vacía. `deploy/seed.sh` la carga por la API con los
datos del Apéndice A del contexto del proyecto:

```bash
export SIGA_ADMIN_PASS=... SIGA_TEACHER_PASS=... SIGA_STUDENT_PASS=...
./deploy/seed.sh                          # contra http://localhost
SIGA_URL=http://mi-instancia ./deploy/seed.sh
```

Carga las 3 carreras con sus planes vigentes, **72 asignaturas con sus
prerrequisitos**, 4 docentes (uno `INACTIVO`), 4 estudiantes (uno en
`POSTERGACION`), los períodos **2025-2** y **2026-1**, y 17 secciones. Después
usa los tres roles para dejar el ciclo completo recorrido: inscribe a
`student@siga.cl` en 2025-2 (con su token), el docente responsable califica, y
el cierre consolida el historial y deja 2025-2 `CERRADO` con 5 asignaturas
aprobadas y 1 reprobada.

Es idempotente: volver a correrlo no duplica nada. Las contraseñas van por
variables de entorno, nunca en el repositorio, y hacen falta las tres porque hay
cosas que solo puede hacer cada rol (el estudiante inscribe, el docente
califica, el administrador cierra).

### Simulación completa (un instituto en marcha)

El seed deja el catálogo, pero el sistema se ve vacío: 4 estudiantes y ninguna
inscripción en el período abierto. `deploy/simulate.ps1` lo llena, también
**solo por la API y con el token del rol que corresponde**:

```
set KEYCLOAK_ADMIN=admin
set KEYCLOAK_ADMIN_PASSWORD=...     REM admin de la consola de Keycloak
set SIGA_ADMIN_PASS=...
set SIGA_TEACHER_PASS=...
set SIGA_STUDENT_PASS=...
set SIGA_SIM_PASS=...               REM password de los usuarios que cree
powershell -ExecutionPolicy Bypass -File deploy\simulate.ps1
```

Deja esto:

| Período | Estado | Qué hay |
| --- | --- | --- |
| 2025-1 | `CERRADO` | 18 secciones, cohorte de 15 en semestre 1, 90 notas. **Lo cierra el script**, así que el historial lo escribió el cierre del sistema, no un `INSERT`. |
| 2025-2 | `CERRADO` | Lo que dejó `seed.sh`. |
| 2026-1 | `ABIERTO` | Semestres 1 y 2 de los **tres** planes: 36 secciones, 12 docentes activos, 28 estudiantes, 162 inscripciones y 92 notas puestas. |

Lo que se puede demostrar con esos datos:

- **Docente**: `teacher@siga.cl` queda con una sección cerrada, una a medias y
  una sin calificar, para mostrar la planilla en los tres estados. El script
  crea 9 docentes más (12 activos en total, 3 por carrera) y les da cuenta en
  Keycloak, igual que a los que venían sin usuario: sin token propio un docente
  no puede calificar, porque el backend lo identifica por el claim `email`.
- **Repitentes**: 7 estudiantes arrastran asignaturas de 2025-1 y el sistema los
  obliga a tomarlas antes que las de segundo semestre. A dos de ellos los
  prerrequisitos les bloquean parte de la oferta, que es la consecuencia
  correcta de haber reprobado.
- **Cierre**: `GET /api/academic-periods/2/closure-readiness` responde
  `canClose=false` con 70 notas pendientes y las 24 asignaturas que lo bloquean.
- **ELIMINADO**: queda armado y se materializa al cerrar 2026-1 — una estudiante
  reprobó TCS105 en 2025-1 y vuelve a reprobarla. No cabe en un solo período,
  porque la regla exige la *segunda* reprobación de la misma asignatura.
- **EGRESADO** no se puede mostrar con estos datos: egresa quien aprueba las 24
  asignaturas de su plan y el instituto simulado lleva dos períodos. La regla
  está implementada y cubierta por los tests.

Es idempotente (volver a correrlo no agrega ni una nota), pero **cerrar 2025-1
no tiene vuelta** y el historial académico no se borra: es la regla del dominio.
Para volver a cero, `docker compose down -v` y repetir seed + simulate.

## CI/CD

`.github/workflows/ci.yml` corre en cada push a `main`, en cada pull request y a
mano (*Run workflow*):

1. **Backend** — pruebas unitarias del *service layer*, después las de
   integración (PostgreSQL con Testcontainers sobre el Docker del runner) y el
   gate de cobertura de JaCoCo. Los reportes quedan como artefacto del run.
2. **Frontend** — `npm ci`, lint y build de producción.
3. **Imágenes** — solo si los dos anteriores pasaron, y nunca desde un pull
   request: construye y publica `siga-backend` y `siga-frontend` en Docker Hub,
   etiquetadas con `latest` (en `main`), el SHA corto del commit y la versión
   cuando el push es de un tag `v*`.

Para que el paso 3 funcione hay que crear **un secreto** en el repositorio
(*Settings → Secrets and variables → Actions*):

| Secreto | Valor |
| --- | --- |
| `DOCKERHUB_TOKEN` | access token de Docker Hub con permiso *Read & Write* |

El usuario de Docker Hub va en el propio workflow (`DOCKERHUB_USER`), porque no
es un secreto y así el nombre de la imagen es visible en los logs.

Para publicar una versión:

```bash
git tag v1.0.0 && git push origin v1.0.0
```

## Despliegue en la nube

`deploy/aws.md` tiene el paso a paso para publicar la demo en una instancia EC2:
tipo de instancia, security group, instalación de Docker y puesta en marcha
desde las imágenes de Docker Hub (la instancia no compila nada).

Lo único que cambia respecto de local es `PUBLIC_URL` en el `.env`: de ella
salen el issuer de los tokens y el valor que valida el backend, así que ambos se
mueven juntos. El frontend no se reconstruye, porque se compila con rutas
relativas.

## Desarrollo local (sin Docker para la app)

Necesita PostgreSQL en `localhost:5432/siga_db` y Keycloak en `localhost:8081`
con el realm `siga-realm`.

```bash
# backend  (lee backend/.env con DB_USERNAME y DB_PASSWORD)
cd backend && cp .env.example .env          # y completar la clave
./mvnw spring-boot:run                      # :8080

# frontend (proxy a :8080 y :8081, sin CORS)
cd frontend && npm install && npm run dev   # :5173
```

## Pruebas

```bash
cd backend
./mvnw test                           # todo: unitarias + integración
./mvnw test -Dtest='*ServiceImplTest' # solo unitarias, sin Docker
./mvnw test -Dtest='*IntegrationTest' # los 18 escenarios, necesita Docker
./mvnw verify                         # + gate de cobertura (JaCoCo, ≥90% en service.impl)
```

Las pruebas de integración levantan PostgreSQL con Testcontainers, así que
necesitan el demonio Docker corriendo. El reporte de cobertura queda en
`backend/target/site/jacoco/index.html`.

```bash
cd frontend
npm run lint
npm run build
```

### Pruebas de carga

`perf/` tiene lo necesario para medir cuántos usuarios aguanta el sistema:
`perf/seed-users.ps1` crea N estudiantes (en Keycloak y en la base, vinculados
por el email) y `perf/load-test.js` sube la carga con k6 hasta el punto de
quiebre. El paso a paso, qué mirar mientras corre y qué se rompe primero están
en `perf/README.md`.

## Reglas de negocio que conviene conocer

- Un solo plan de estudios **VIGENTE** por carrera; al dejar uno vigente, el
  anterior pasa a `NO_VIGENTE`. El estudiante conserva el plan con el que entró.
- El horario de una sección tiene exactamente `(teoría + ejercicios + lab) / 2`
  bloques, sin choques de día y módulo para el mismo docente en el período.
- La inscripción es **por lote**: entre 3 y 6 asignaturas (el mínimo baja si
  quedan menos disponibles), con prerrequisitos aprobados, cupos libres, sin
  choques de horario y sin saltarse semestres.
- La nota es única por inscripción (1.0 a 7.0, aprueba con 4.0) y solo la
  registra el docente responsable, mientras el período esté `ABIERTO`.
- El **cierre de período** es la única vía para que un período quede `CERRADO`:
  exige todas las notas, copia los resultados al historial académico y define
  los estados terminales `EGRESADO` y `ELIMINADO`. No tiene retorno.

Anular una inscripción no borra la fila: la deja en `ANULADA` y libera el cupo.
Lo mismo con el resto de los registros académicos — los borrados que romperían
una referencia se rechazan con `409`.

## Documentación

- `CLAUDE.md` — arquitectura, convenciones y las decisiones no obvias de cada capa.
- `Plan_de_Proyecto.md` y `Contexto_Proyecto.md` — alcance y plan de fases.
- `keycloak/README.md` — qué debe contener el realm y cómo exportarlo.
