# Pruebas de carga

Responde dos preguntas: **cuántos usuarios aguanta** el sistema antes de
ralentizarse o dejar de responder, y **qué se rompe primero**.

Son dos pasos: poblar el sistema con usuarios de verdad (Keycloak + base de
datos) y después golpearlo subiendo la carga hasta que se quiebre.

| Archivo | Qué es |
| --- | --- |
| `seed-users.ps1` | Crea N estudiantes en Keycloak y en SIGA. Idempotente. |
| `load-test.js` | La prueba de carga (k6). Escalona la carga y aborta en el punto de quiebre. |
| `users.json` | Lo genera el primero y lo lee el segundo. No va al repositorio. |

## 1. Poblar con usuarios

Cada estudiante tiene que existir **dos veces**: como usuario de Keycloak (es
quien se autentica) y como `Student` en la base (es quien tiene plan, inscripciones
e historial). El vínculo entre ambos es el **email**: el backend resuelve
`/api/students/me` con el claim `email` del token, nunca con un id del body.

Con el stack arriba y los datos de demo cargados (`deploy/seed.sh`):

```bat
set KEYCLOAK_ADMIN=admin
set KEYCLOAK_ADMIN_PASSWORD=...        REM admin de la consola de Keycloak
set KC_CLIENT_ID=siga-frontend         REM cliente con "Direct access grants"
set KC_ADMIN_USER=admin                REM username (no el email) del ADMIN del realm
set KC_ADMIN_PASS=...
set PERF_USER_PASS=Perf.2026

powershell -ExecutionPolicy Bypass -File perf\seed-users.ps1 -Count 100 -Matriculate
```

- `-Count 100` → `perf-student-0001` … `perf-student-0100`, con email
  `perf-student.0001@perf.siga.cl` y RUN sintético `90000001-0`.
- `-Matriculate` los matricula en el período `ABIERTO` más reciente. **Hace
  falta**: sin matrícula, `GET /api/enrollments/available` responde vacío y la
  prueba mediría una consulta trivial.
- `-CareerCode 10450` para elegir la carrera (por defecto, la primera).
- Volver a correrlo no duplica nada: reutiliza lo que ya existe.
- `-Remove` deshace lo creado. Los estudiantes que ya tengan inscripciones o
  historial **no** se borran: los protege el 409 del propio sistema.

Deja `perf/users.json` con los usuarios, el id del período y el cliente. Las
contraseñas no se escriben ahí: viajan por `PERF_USER_PASS`.

> **Cuántos crear.** 100 alcanza para encontrar el techo: la carga no la hacen
> los usuarios registrados sino las peticiones por segundo, y k6 reutiliza los
> tokens de esos 100 para generar mucho más tráfico del que harían 100 personas
> reales. Si quieres medir el *volumen de datos* (tablas grandes), ahí sí sube a
> 1000 o más.

## 2. La prueba de carga

La herramienta es [k6](https://k6.io): no hay que instalar nada si ya tienes
Docker, y se corre **dentro de la red del stack** para no medir la traducción de
puertos de Docker Desktop.

```bash
# desde la raíz del proyecto, con el stack arriba
docker run --rm -i \
  --network siga_siga-net \
  -v "$PWD/perf:/perf" \
  -e PERF_USER_PASS=Perf.2026 \
  -e SIGA_URL=http://nginx \
  -e MAX_RPS=120 \
  grafana/k6:latest run /perf/load-test.js
```

En `cmd` de Windows, la misma línea con `%cd%` y sin las barras invertidas:

```bat
docker run --rm -i --network siga_siga-net -v "%cd%\perf:/perf" -e PERF_USER_PASS=Perf.2026 -e SIGA_URL=http://nginx -e MAX_RPS=120 grafana/k6:latest run /perf/load-test.js
```

Si prefieres k6 instalado en el sistema (`winget install k6`), se corre
`k6 run perf/load-test.js` sin `SIGA_URL` y la prueba sale por `http://localhost`.

### Los modos

| `-e MODE=` | Qué hace | Para qué |
| --- | --- | --- |
| `smoke` | 1 usuario, 1 vuelta | Comprobar credenciales y escenario antes de medir |
| `ramp` (defecto) | Sube de 10% a `MAX_RPS` en 10 escalones de 45 s | **Encontrar el punto de quiebre** |
| `soak` | `RPS` fijo durante `DURATION` | Ver si aguanta sostenido lo que aguantó un rato |
| `write` | Escalona `PUT /api/student-period-status` | Carga de escritura |

Otras variables: `MAX_RPS` (techo, 120), `RPS` (tasa fija del soak, 30),
`STAGE` (duración de cada escalón, 45s), `P95_MS` (umbral de latencia, 1500),
`DURATION` (soak, 5m).

**Empieza siempre por `MODE=smoke`**: si las credenciales o el período están
mal, lo ves en 5 segundos en vez de a mitad de una prueba de 8 minutos.

### Qué mide una vuelta

Una iteración es la **sesión de un estudiante**, no una petición suelta:

```
GET /api/students/me
GET /api/academic-periods?status=ABIERTO
GET /api/enrollments/available?academicPeriodId=…   <- la pesada
GET /api/enrollments/me
GET /api/academic-history/me
```

`enrollments/available` es la consulta más costosa del sistema (por cada sección
candidata evalúa prerrequisitos, cupos y choques de horario), así que es la que
marca el techo. El resumen de k6 desglosa por endpoint, porque cada petición
lleva su nombre en un tag.

El modo `write` usa la matrícula porque es el **único write repetible**: es un
`PUT` idempotente, así que mil pasadas dejan la base igual que una. La
inscripción no sirve (cada estudiante inscribe una vez por período) ni la nota
(una por inscripción).

## 3. Leer el resultado

La prueba **aborta sola** cuando rompe uno de los dos umbrales:

- `http_req_failed rate<0.01` → más del 1% de errores (502/504/timeouts).
- `http_req_duration p(95)<1500` → el 5% más lento pasa de 1,5 s.

El escalón en el que abortó **es la respuesta**: k6 imprime la tasa de llegada
en vivo (`iterations/s`) y, al final, el total de peticiones, la latencia por
percentil y el desglose por endpoint. Si no aborta, el sistema aguantó
`MAX_RPS`: súbelo y repite.

```
     ✓ students/me 200
     ✗ enrollments/available 200
      ↳  93% — ✓ 1402 / ✗ 98

     http_req_duration..............: avg=412ms p(95)=1.82s   <- rompió aquí
     http_req_failed................: 2.14%
     iterations.....................: 64/s
```

Un dato útil: `reauth_total` cuenta los tokens que vencieron a mitad de la
prueba. Si es alto, el access token del realm (5 min por defecto) dura menos que
la prueba; se sube en Keycloak → Realm settings → Tokens.

### Mirar el sistema mientras corre

Tres terminales en paralelo dicen casi todo:

```bash
# 1. Recursos por contenedor: CPU% y memoria contra el límite de 640 MB
docker stats

# 2. Quién atendió cada petición y cuánto tardó (log_format siga)
docker compose logs -f nginx
#    172.18.0.9 "GET /api/... " -> 172.18.0.5:8080 200 0.412s
#    un 502/504 aquí = una réplica dejó de responder
#    si todas las líneas van al mismo upstream_addr, el balanceo no está rotando

# 3. Conexiones a la base
docker compose exec db psql -U siga -d siga_db -c "select state, count(*) from pg_stat_activity where datname='siga_db' group by state"
```

Y las métricas del backend, que necesitan token (solo `/actuator/health` es
público):

```bash
TOKEN=...   # un token de cualquier rol
curl -s -H "Authorization: Bearer $TOKEN" localhost/actuator/metrics/hikaricp.connections.pending
curl -s -H "Authorization: Bearer $TOKEN" localhost/actuator/metrics/jvm.memory.used
curl -s -H "Authorization: Bearer $TOKEN" localhost/actuator/metrics/http.server.requests
```

`hikaricp.connections.pending` es el dato más revelador: si sube, los hilos están
esperando una conexión a la base y el cuello de botella es el pool, no la CPU.

## Resultados medidos (31-10-2026)

Primera corrida real, para tener una referencia. **Windows 10, 8 núcleos, 8 GB**,
con el stack de dos réplicas *y k6 en la misma máquina*, 100 estudiantes de
prueba matriculados en 2026-1.

| Escenario | Resultado |
| --- | --- |
| `smoke` | 5/5 endpoints 200, p95 **60 ms** |
| `ramp` a 120 iter/s (≈600 req/s) | aguanta todo el escalonado: p95 **10 ms**, 0 % de error, 13 VUs |
| `ramp` a 1200 iter/s | **aborta en el escalón 5/10**: ~**517 iter/s** sostenidos (≈977 req/s) al cruzar p95 > 1,5 s. Sin un solo error HTTP: se degrada, no se cae |
| `soak` 400 iter/s, 90 s | **1.421 req/s**, p95 1,35 s, **0 %** de error, 135.130 comprobaciones correctas |
| Réplica caída bajo carga | `docker compose stop backend-2` a mitad de un soak de 100 iter/s: **35.065 peticiones, 0 % de error**, p95 46 ms. Ni un 502/503/504 en el log de nginx |

Muestreado durante el sostenido de 400 iter/s, **con el pool en su defecto de
10**:

```
hikari activas=10  pendientes=183      <- 10 es el máximo del pool; 183 hilos esperando
conexiones en postgres=21              <- de 100 que acepta: la base sobra
backend-1 CPU 211%  mem 522/640 MB
backend-2 CPU 219%  mem 574/640 MB
db        CPU 155%  mem  75 MB
keycloak  CPU 0,3%                     <- los tokens se piden una vez, no se mide el login
```

Las dos réplicas más la base consumen ~7 de los 8 núcleos, así que CPU y pool
llegan al límite casi juntos. Lo que **no** fue el límite: la memoria (ninguna
réplica llegó a los 640 MB), Postgres (21 de 100 conexiones) ni Keycloak.

El reparto entre réplicas quedó 166.334 / 160.182 peticiones (51 % / 49 %):
round robin funcionando, comprobable en el log de nginx.

### El pool NO era el techo: 10 vs 25 conexiones

Lo anterior apuntaba al pool, así que se subió a 25 por réplica
(`DB_POOL_SIZE`, 51 conexiones en Postgres) y se repitió **la misma prueba**:

| | pool 10 | pool 25 |
| --- | --- | --- |
| Quiebre del `ramp` (p95 > 1,5 s) | 517 iter/s · 977 req/s | **507 iter/s · 1.001 req/s** |
| `soak` 400 iter/s | 1.421 req/s · p95 1,35 s | **1.491 req/s · p95 1,30 s** |
| Errores | 0 % | 0 % |

**El techo no se movió** (±2 % en el quiebre, +5 % en el sostenido). Lo que se
movió fue la cola: de esperar por una conexión a esperar por CPU. La prueba está
en el muestreo con el pool en 25:

```
hikari 25/25 activas, 170 esperando    <- el pool sigue "lleno"...
queries activas en postgres = 1        <- ...pero la base no está haciendo nada
backend-1 CPU 225%   backend-2 CPU 247%   db CPU 170%
```

Veinticinco conexiones *tomadas* y una sola consulta corriendo: las conexiones
están retenidas por hilos que no están consultando. El servicio es
`@Transactional` de principio a fin, así que cada hilo retiene su conexión
mientras hace el trabajo de CPU (resolver las reglas de inscripción, mapear
entidades a DTO, serializar), que es donde se va el tiempo. Agrandar el pool solo
permite que más hilos esperen a la vez por los mismos núcleos.

**Conclusión: el límite es CPU de la aplicación, no la base.** Lo que movería el
techo de verdad, en orden de rendimiento por esfuerzo: más núcleos o más réplicas
(en máquinas distintas, no en la misma), reducir el trabajo por petición en
`enrollments/available` —que hoy recalcula prerrequisitos, cupos y choques por
cada candidata— y acortar la transacción para que no abarque la serialización.
Con la base a 1-6 consultas activas, tocar Postgres no compra nada.

> El número de 517 iter/s está **subestimado**: k6 llegó a abrir 2.300 VUs en la
> misma máquina que el stack y aparecen `dropped_iterations` (k6 no alcanzó a
> generar la tasa pedida). Para una cifra limpia, generar la carga desde otra
> máquina.

## 4. Qué se rompe primero (y cómo moverlo)

Según cómo está configurado el stack hoy, en este orden:

1. **La CPU de la aplicación.** Es el límite real, medido: ~1.000 req/s con dos
   réplicas en 8 núcleos. `hikaricp.connections.pending` sube y hace pensar en el
   pool, pero si `pg_stat_activity` muestra pocas consultas activas al mismo
   tiempo, las conexiones están retenidas por hilos que están gastando CPU, no
   esperando a la base: agrandar el pool no mueve el techo (medido arriba).
   El pool (`DB_POOL_SIZE`, 25 por réplica) solo importa que no quede por debajo
   de los hilos que de verdad consultan, y que réplicas × pool se mantenga bajo
   el `max_connections` de Postgres (100).
2. **La CPU de tu máquina, por el lado del generador.** Dos JVM, Keycloak,
   Postgres, nginx *y k6* compiten por los mismos núcleos. En `docker stats` se ve como CPU% pegado al techo en
   todos los contenedores a la vez. Es el límite más habitual en un portátil, y
   significa que estás midiendo tu PC, no el sistema.
3. **La memoria de las réplicas** (`mem_limit: 640m`). Si una se acerca al
   límite, el contenedor muere por OOM y nginx la saca de rotación: en el log
   aparecen 502 durante unos segundos y luego el sistema sigue con las otras.
   Eso también es un resultado: **el balanceo funciona**.
4. **Keycloak**, solo si pidieras un token por petición. La prueba no lo hace a
   propósito: los tokens se obtienen una vez en `setup()`, porque medir el login
   es medir Keycloak, no SIGA.

> **La advertencia honesta.** Corriendo todo en un portátil, el número que salga
> mide *tu máquina con todo encima*, no la capacidad del sistema desplegado. Para
> una cifra defendible, corre la carga desde una máquina distinta a la que tiene
> el stack (por ejemplo, k6 local contra la instancia de EC2). Lo que sí es
> perfectamente válido en local es lo **comparativo**: dos réplicas contra tres,
> antes y después de tocar el pool, o qué endpoint se cae primero.
