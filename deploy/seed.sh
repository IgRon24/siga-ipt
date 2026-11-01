#!/usr/bin/env bash
#
# Carga los datos iniciales de SIGA por la API: las tres carreras del Apendice A
# con sus planes, asignaturas y prerrequisitos, docentes, estudiantes, dos
# periodos academicos (2025-2 cerrado con historial y 2026-1 abierto) y la
# oferta de secciones.
#
# Es idempotente: vuelve a correrse sin duplicar nada.
#
# Uso:
#   export SIGA_ADMIN_PASS=...    SIGA_TEACHER_PASS=...   SIGA_STUDENT_PASS=...
#   ./deploy/seed.sh                         # contra http://localhost
#   SIGA_URL=http://mi-instancia ./deploy/seed.sh
#
# Necesita los tres usuarios del realm (admin@siga.cl, teacher@siga.cl y
# student@siga.cl) porque hay cosas que solo puede hacer cada rol: el ADMIN crea
# el catalogo y cierra el periodo, el STUDENT inscribe -su id sale del token,
# nunca del cuerpo- y el TEACHER responsable califica.

set -uo pipefail

BASE_URL=${SIGA_URL:-http://localhost}
KC_REALM=${SIGA_KC_REALM:-siga-realm}
KC_CLIENT=${SIGA_KC_CLIENT:-siga-frontend}

ADMIN_USER=${SIGA_ADMIN_USER:-admin@siga.cl}
TEACHER_USER=${SIGA_TEACHER_USER:-teacher@siga.cl}
STUDENT_USER=${SIGA_STUDENT_USER:-student@siga.cl}

PY=$(command -v python3 || command -v python)
if [ -z "$PY" ]; then
    echo "Falta python3 (se usa solo para leer el JSON de las respuestas)." >&2
    exit 1
fi

for var in SIGA_ADMIN_PASS SIGA_TEACHER_PASS SIGA_STUDENT_PASS; do
    if [ -z "${!var:-}" ]; then
        echo "Falta la variable $var." >&2
        echo "Las contrasenas no viajan en el repositorio: pasalas por el entorno." >&2
        echo "  export SIGA_ADMIN_PASS=...  SIGA_TEACHER_PASS=...  SIGA_STUDENT_PASS=..." >&2
        exit 1
    fi
done

created=0; existing=0; failed=0

# ---------------------------------------------------------------
# Utilidades
# ---------------------------------------------------------------

# Los mensajes van a stderr a proposito: varias de estas funciones devuelven un
# id por stdout y se leen con $(...), asi que esa salida tiene que quedar limpia.
banner() { printf '\n== %s %s\n' "$1" "$(printf '=%.0s' $(seq $((60 - ${#1}))))" >&2; }
ok()     { printf '   + %s\n' "$1" >&2; created=$((created + 1)); }
skip()   { printf '   = %s\n' "$1" >&2; existing=$((existing + 1)); }
err()    { printf '   ! %s\n' "$1" >&2; failed=$((failed + 1)); }

get_token() {
    local user=$1 pass=$2
    curl -sS -X POST "$BASE_URL/auth/realms/$KC_REALM/protocol/openid-connect/token" \
        -d grant_type=password -d "client_id=$KC_CLIENT" \
        -d "username=$user" -d "password=$pass" |
        "$PY" -c 'import sys,json
try: print(json.load(sys.stdin)["access_token"])
except Exception: print("")'
}

api() { # metodo ruta [body] [token]
    local method=$1 path=$2 body=${3:-} token=${4:-$ADMIN_TOKEN}
    if [ -n "$body" ]; then
        curl -sS -X "$method" -H "Authorization: Bearer $token" \
            -H 'Content-Type: application/json' -d "$body" "$BASE_URL/api$path"
    else
        curl -sS -X "$method" -H "Authorization: Bearer $token" "$BASE_URL/api$path"
    fi
}

# id del primer elemento de una lista cuyo campo coincide, o vacio
find_id() { # ruta campo valor [token]
    api GET "$1" '' "${4:-$ADMIN_TOKEN}" | "$PY" -c 'import sys,json
field, value = sys.argv[1], sys.argv[2]
try: data = json.load(sys.stdin)
except Exception: data = []
if not isinstance(data, list): data = []
print(next((str(x["id"]) for x in data if str(x.get(field)) == value), ""))' "$2" "$3"
}

# crea y devuelve el id; si el backend rechaza, lo informa y devuelve vacio
create_id() { # ruta body etiqueta
    local response
    response=$(api POST "$1" "$2")
    local id
    id=$(printf '%s' "$response" | "$PY" -c 'import sys,json
try: print(json.load(sys.stdin).get("id",""))
except Exception: print("")')
    if [ -n "$id" ]; then
        ok "$3"
        printf '%s' "$id"
    else
        err "$3 -> $(printf '%s' "$response" | "$PY" -c 'import sys,json
try:
    d=json.load(sys.stdin); m=d.get("message", d)
    print(m if isinstance(m,str) else json.dumps(m, ensure_ascii=False))
except Exception: print("sin respuesta")')"
    fi
}

# Bloques horarios unicos dentro del periodo: dia = indice/6, modulo = indice%6+1.
# Asignar bloques distintos a cada seccion evita de una vez los dos choques que
# valida el backend: el del docente y el del horario del estudiante.
DAYS=(L M W J V)
slot_index=0
reset_slots() { slot_index=0; }

# Deja el JSON en BLOCKS_JSON en vez de imprimirlo: si se leyera con $(...)
# correria en un subshell y el indice volveria a empezar en cada seccion,
# dejando a todas en el mismo bloque.
BLOCKS_JSON=""
take_blocks() { # cantidad
    local count=$1 parts=() d m
    while [ "$count" -gt 0 ]; do
        d=${DAYS[$((slot_index / 6))]}
        m=$((slot_index % 6 + 1))
        parts+=("{\"day\":\"$d\",\"module\":$m}")
        slot_index=$((slot_index + 1))
        count=$((count - 1))
    done
    local IFS=,
    BLOCKS_JSON="[${parts[*]}]"
}

# ---------------------------------------------------------------
# Tokens
# ---------------------------------------------------------------

banner "AUTENTICACION"
ADMIN_TOKEN=$(get_token "$ADMIN_USER" "$SIGA_ADMIN_PASS")
[ -z "$ADMIN_TOKEN" ] && { echo "   No se pudo autenticar a $ADMIN_USER en $BASE_URL" >&2; exit 1; }
printf '   admin   OK\n' >&2
TEACHER_TOKEN=$(get_token "$TEACHER_USER" "$SIGA_TEACHER_PASS")
[ -z "$TEACHER_TOKEN" ] && { echo "   No se pudo autenticar a $TEACHER_USER" >&2; exit 1; }
printf '   docente OK\n' >&2
STUDENT_TOKEN=$(get_token "$STUDENT_USER" "$SIGA_STUDENT_PASS")
[ -z "$STUDENT_TOKEN" ] && { echo "   No se pudo autenticar a $STUDENT_USER" >&2; exit 1; }
printf '   alumno  OK\n' >&2

# ---------------------------------------------------------------
# Carreras y planes de estudio
# ---------------------------------------------------------------

banner "CARRERAS Y PLANES"

ensure_career() { # codigo nombre descripcion -> id
    local id
    id=$(find_id /careers code "$1")
    if [ -n "$id" ]; then skip "carrera $1"; printf '%s' "$id"; return; fi
    create_id /careers \
        "{\"code\":\"$1\",\"name\":\"$2\",\"description\":\"$3\",\"status\":\"ACTIVA\"}" \
        "carrera $1 $2"
}

ensure_plan() { # careerId identificador nombre -> id
    local id
    id=$(find_id "/study-plans/career/$1" identifier "$2")
    if [ -n "$id" ]; then skip "plan $2"; printf '%s' "$id"; return; fi
    create_id /study-plans \
        "{\"identifier\":\"$2\",\"name\":\"$3\",\"status\":\"VIGENTE\",\"careerId\":$1}" \
        "plan $2 (vigente)"
}

CAR_TAP=$(ensure_career 10450 "Tecnico Analista Programador" "Carrera tecnica de nivel superior, 4 semestres")
CAR_TAD=$(ensure_career 10451 "Tecnico en Analisis de Datos" "Carrera tecnica de nivel superior, 4 semestres")
CAR_TCS=$(ensure_career 10452 "Tecnico en Ciberseguridad" "Carrera tecnica de nivel superior, 4 semestres")

PLAN_TAP=$(ensure_plan "$CAR_TAP" "2021.3" "Plan de estudios 2021.3 Analista Programador")
PLAN_TAD=$(ensure_plan "$CAR_TAD" "2022.3" "Plan de estudios 2022.3 Analisis de Datos")
PLAN_TCS=$(ensure_plan "$CAR_TCS" "2021.3" "Plan de estudios 2021.3 Ciberseguridad")

# ---------------------------------------------------------------
# Asignaturas y prerrequisitos (Apendice A completo)
# ---------------------------------------------------------------

banner "ASIGNATURAS"

ensure_subject() { # planId codigo nombre sem T E L SCT -> id
    local id
    id=$(find_id "/subjects/study-plan/$1" code "$2")
    if [ -n "$id" ]; then skip "asignatura $2"; printf '%s' "$id"; return; fi
    create_id /subjects \
        "{\"code\":\"$2\",\"name\":\"$3\",\"semester\":$4,\"telTheory\":$5,\"telExercises\":$6,\"telLab\":$7,\"sct\":$8,\"studyPlanId\":$1}" \
        "asignatura $2 $3"
}

# Las asignaturas se cargan desde una tabla: codigo|nombre|sem|T|E|L|SCT|prerrequisitos
load_plan() { # planId <<< lineas
    local plan=$1 line code name sem t e l sct prereqs
    local -a codes=() prereq_lines=()

    while IFS='|' read -r code name sem t e l sct prereqs; do
        [ -z "${code:-}" ] && continue
        ensure_subject "$plan" "$code" "$name" "$sem" "$t" "$e" "$l" "$sct" > /dev/null
        [ -n "${prereqs:-}" ] && prereq_lines+=("$code|$prereqs")
    done

    # Los prerrequisitos van despues: la asignatura exigida tiene que existir
    local target required sid pid
    for line in "${prereq_lines[@]}"; do
        target=${line%%|*}
        required=${line#*|}
        sid=$(find_id "/subjects/study-plan/$plan" code "$target")
        [ -z "$sid" ] && continue
        local IFS=,
        for required_code in $required; do
            unset IFS
            pid=$(find_id "/subjects/study-plan/$plan" code "$required_code")
            [ -z "$pid" ] && continue
            if api GET "/subjects/$sid/prerequisites" | grep -q "\"prerequisiteSubjectCode\":\"$required_code\""; then
                skip "prerrequisito $target <- $required_code"
            else
                if api POST "/subjects/$sid/prerequisites" "{\"prerequisiteSubjectId\":$pid}" | grep -q '"id"'; then
                    ok "prerrequisito $target <- $required_code"
                else
                    err "prerrequisito $target <- $required_code"
                fi
            fi
        done
    done
}

printf '  10450 Analista Programador\n' >&2
load_plan "$PLAN_TAP" <<'EOF'
TAP101|Fundamentos de Programacion|1|2|2|2|6|
TAP102|Matematica Aplicada|1|2|2|0|5|
TAP103|Fundamentos de Computacion|1|2|0|2|4|
TAP104|Bases de Datos I|1|2|0|2|5|
TAP105|Comunicacion Efectiva|1|2|2|0|4|
TAP106|Ingles Tecnico I|1|2|2|0|4|
TAP201|Programacion Orientada a Objetos|2|2|2|2|6|TAP101
TAP202|Estructuras de Datos|2|2|2|2|6|TAP101
TAP203|Bases de Datos II|2|2|0|2|5|TAP104
TAP204|Desarrollo Web I|2|2|0|4|6|TAP101
TAP205|Sistemas Operativos|2|2|0|2|4|TAP103
TAP206|Ingles Tecnico II|2|2|2|0|3|TAP106
TAP301|Desarrollo Web II|3|2|0|4|6|TAP201,TAP204
TAP302|Ingenieria de Software|3|2|2|0|5|TAP201
TAP303|Desarrollo de Aplicaciones Moviles|3|2|0|4|6|TAP201
TAP304|Administracion de Bases de Datos|3|2|0|2|5|TAP203
TAP305|Redes y Comunicaciones|3|2|0|2|4|TAP205
TAP306|Taller de Programacion|3|0|0|4|4|TAP201,TAP202
TAP401|Arquitectura y Desarrollo de Software|4|2|0|4|6|TAP301,TAP302
TAP402|Desarrollo de Aplicaciones Cloud|4|2|0|4|6|TAP301
TAP403|Seguridad de Aplicaciones|4|2|0|2|4|TAP301,TAP305
TAP404|Calidad y Pruebas de Software|4|2|0|2|5|TAP302
TAP405|Proyecto de Desarrollo de Software|4|0|0|6|6|TAP301,TAP302,TAP306
TAP406|Emprendimiento y Gestion de Proyectos|4|2|2|0|3|TAP302
EOF

printf '  10451 Analisis de Datos\n' >&2
load_plan "$PLAN_TAD" <<'EOF'
TAD101|Fundamentos de Programacion|1|2|2|2|6|
TAD102|Matematica Aplicada|1|2|2|0|5|
TAD103|Introduccion al Analisis de Datos|1|2|0|2|5|
TAD104|Bases de Datos I|1|2|0|2|5|
TAD105|Herramientas Computacionales|1|0|0|4|4|
TAD106|Comunicacion Efectiva|1|2|2|0|4|
TAD201|Programacion para Analisis de Datos|2|2|0|4|6|TAD101
TAD202|Probabilidad y Estadistica|2|2|2|2|6|TAD102
TAD203|Bases de Datos II|2|2|0|2|5|TAD104
TAD204|Preparacion y Calidad de Datos|2|2|0|4|5|TAD103,TAD101
TAD205|Visualizacion de Datos|2|2|0|2|5|TAD103
TAD206|Ingles Tecnico|2|2|2|0|3|
TAD301|Estadistica Aplicada|3|2|2|2|6|TAD202
TAD302|Mineria de Datos|3|2|0|4|6|TAD201,TAD202
TAD303|Inteligencia de Negocios|3|2|0|4|5|TAD203,TAD205
TAD304|Integracion y Procesamiento de Datos|3|2|0|4|5|TAD201,TAD203
TAD305|Visualizacion Avanzada de Datos|3|2|0|2|4|TAD205
TAD306|Etica y Proteccion de Datos|3|2|2|0|4|TAD103
TAD401|Fundamentos de Machine Learning|4|2|0|4|6|TAD301,TAD302
TAD402|Big Data y Procesamiento de Datos|4|2|0|4|5|TAD304
TAD403|Analitica de Negocios|4|2|0|2|5|TAD301,TAD303
TAD404|Desarrollo de Soluciones de Datos|4|2|0|4|5|TAD302,TAD304
TAD405|Proyecto de Analisis de Datos|4|0|0|6|6|TAD302,TAD303,TAD305
TAD406|Emprendimiento y Gestion de Proyectos|4|2|2|0|3|
EOF

printf '  10452 Ciberseguridad\n' >&2
load_plan "$PLAN_TCS" <<'EOF'
TCS101|Fundamentos de Programacion|1|2|2|2|6|
TCS102|Fundamentos de Sistemas Computacionales|1|2|0|2|5|
TCS103|Redes de Computadores I|1|2|0|2|5|
TCS104|Sistemas Operativos|1|2|0|2|5|
TCS105|Matematica Aplicada|1|2|2|0|5|
TCS106|Comunicacion Efectiva|1|2|2|0|4|
TCS201|Programacion para Ciberseguridad|2|2|0|4|5|TCS101
TCS202|Redes de Computadores II|2|2|0|4|6|TCS103
TCS203|Administracion de Sistemas Linux|2|2|0|4|5|TCS104
TCS204|Fundamentos de Ciberseguridad|2|2|2|2|6|TCS102,TCS103
TCS205|Bases de Datos|2|2|0|2|5|TCS101
TCS206|Ingles Tecnico|2|2|2|0|3|
TCS301|Seguridad de Redes|3|2|0|4|6|TCS202,TCS204
TCS302|Seguridad de Sistemas Operativos|3|2|0|4|5|TCS203,TCS204
TCS303|Criptografia Aplicada|3|2|2|2|5|TCS204
TCS304|Seguridad de Aplicaciones Web|3|2|0|4|5|TCS201,TCS204
TCS305|Monitoreo y Deteccion de Amenazas|3|2|0|4|5|TCS202,TCS204
TCS306|Legislacion y Etica en Ciberseguridad|3|2|2|0|4|TCS204
TCS401|Ethical Hacking y Pruebas de Penetracion|4|2|0|4|6|TCS301,TCS302
TCS402|Gestion de Incidentes de Ciberseguridad|4|2|0|4|5|TCS305
TCS403|Seguridad en Cloud|4|2|0|4|5|TCS301,TCS302
TCS404|Analisis Forense Digital|4|2|0|4|5|TCS302
TCS405|Proyecto de Ciberseguridad|4|0|0|6|6|TCS301,TCS302,TCS305
TCS406|Gestion de Riesgos de Ciberseguridad|4|2|2|0|3|TCS204
EOF

# ---------------------------------------------------------------
# Docentes
# ---------------------------------------------------------------

banner "DOCENTES"

ensure_teacher() { # run nombre email titulo grado -> id
    local id
    id=$(find_id /teachers email "$4")
    if [ -n "$id" ]; then skip "docente $4"; printf '%s' "$id"; return; fi
    create_id /teachers \
        "{\"run\":\"$1\",\"fullName\":\"$2\",\"email\":\"$4\",\"professionalTitle\":\"$3\",\"academicDegree\":\"$5\"}" \
        "docente $2"
}

# El primero es el usuario de Keycloak: es el que puede calificar en la demo
T_MAIN=$(ensure_teacher "15111222-3" "Ana Perez Soto" "Ingeniera Civil en Informatica" "$TEACHER_USER" "Magister en Ciencias de la Computacion")
T_TWO=$(ensure_teacher "16222333-4" "Luis Rojas Vera" "Ingeniero en Computacion" "luis.rojas@siga.cl" "Magister en Ingenieria de Software")
T_THREE=$(ensure_teacher "17333444-5" "Marta Diaz Lagos" "Ingeniera en Estadistica" "marta.diaz@siga.cl" "Doctora en Estadistica")
T_INACTIVE=$(ensure_teacher "18444555-6" "Jorge Soto Mena" "Ingeniero en Redes" "jorge.soto@siga.cl" "Magister en Ciberseguridad")

if [ -n "$T_INACTIVE" ]; then
    estado=$(api GET "/teachers/$T_INACTIVE" | "$PY" -c 'import sys,json;print(json.load(sys.stdin).get("status",""))')
    if [ "$estado" = "INACTIVO" ]; then
        skip "docente Jorge Soto ya INACTIVO"
    else
        api PATCH "/teachers/$T_INACTIVE/status?status=INACTIVO" > /dev/null && ok "docente Jorge Soto -> INACTIVO (para mostrar la regla)"
    fi
fi

# ---------------------------------------------------------------
# Estudiantes
# ---------------------------------------------------------------

banner "ESTUDIANTES"

ensure_student() { # run nombre email careerId -> id
    local id
    id=$(find_id /students email "$3")
    if [ -n "$id" ]; then skip "estudiante $3"; printf '%s' "$id"; return; fi
    create_id /students \
        "{\"run\":\"$1\",\"fullName\":\"$2\",\"email\":\"$3\",\"careerId\":$4}" \
        "estudiante $2"
}

# El primero es el usuario de Keycloak: es el que inscribe en la demo
S_MAIN=$(ensure_student "20111222-3" "Pedro Munoz Lara" "$STUDENT_USER" "$CAR_TAP")
S_TWO=$(ensure_student "20222333-4" "Camila Vera Rios" "camila.vera@siga.cl" "$CAR_TAP")
S_THREE=$(ensure_student "20333444-5" "Diego Castro Pino" "diego.castro@siga.cl" "$CAR_TAD")
S_FOUR=$(ensure_student "20444555-6" "Valentina Soto Paz" "valentina.soto@siga.cl" "$CAR_TCS")

if [ -n "$S_FOUR" ]; then
    estado=$(api GET "/students/$S_FOUR" | "$PY" -c 'import sys,json;print(json.load(sys.stdin).get("status",""))')
    if [ "$estado" = "POSTERGACION" ]; then
        skip "estudiante Valentina ya en POSTERGACION"
    elif [ "$estado" = "REGULAR" ]; then
        api PATCH "/students/$S_FOUR/status?status=POSTERGACION" > /dev/null && ok "estudiante Valentina -> POSTERGACION (para mostrar la regla)"
    fi
fi

# ---------------------------------------------------------------
# Periodos y oferta academica
# ---------------------------------------------------------------

banner "PERIODOS Y SECCIONES"

ensure_period() { # anio semestre -> id
    local id
    id=$(find_id /academic-periods code "$1-$2")
    if [ -n "$id" ]; then skip "periodo $1-$2"; printf '%s' "$id"; return; fi
    create_id /academic-periods "{\"year\":$1,\"semester\":$2}" "periodo $1-$2"
}

ensure_section() { # planId codigo periodoId docenteId capacidad -> id
    local subject_id tel id
    subject_id=$(find_id "/subjects/study-plan/$1" code "$2")
    [ -z "$subject_id" ] && { err "seccion $2: la asignatura no existe"; return; }

    id=$(find_id "/sections?academicPeriodId=$3" subjectCode "$2")
    if [ -n "$id" ]; then skip "seccion $2"; printf '%s' "$id"; return; fi

    # La cantidad de bloques la define la asignatura: (T + E + L) / 2
    tel=$(api GET "/subjects/$subject_id" | "$PY" -c 'import sys,json
d=json.load(sys.stdin)
print((d["telTheory"]+d["telExercises"]+d["telLab"])//2)')
    take_blocks "$tel"

    create_id /sections \
        "{\"subjectId\":$subject_id,\"academicPeriodId\":$3,\"teacherId\":$4,\"capacity\":$5,\"scheduleBlocks\":$BLOCKS_JSON}" \
        "seccion $2 ($tel bloques, $BLOCKS_JSON)"
}

PER_PAST=$(ensure_period 2025 2)
PER_OPEN=$(ensure_period 2026 1)

past_status=$(api GET "/academic-periods/$PER_PAST" | "$PY" -c 'import sys,json;print(json.load(sys.stdin).get("status",""))')

# --- 2025-2: primer semestre de Analista Programador ---
# Todas a cargo del docente de Keycloak, porque es el unico que puede calificar
# en la demo (el backend exige el docente responsable del token).
if [ "$past_status" = "ABIERTO" ]; then
    printf '  2025-2 (se va a cerrar)\n' >&2
    reset_slots
    for code in TAP101 TAP102 TAP103 TAP104 TAP105 TAP106; do
        ensure_section "$PLAN_TAP" "$code" "$PER_PAST" "$T_MAIN" 35 > /dev/null
    done
else
    skip "2025-2 ya esta CERRADO: no se toca su oferta"
fi

# --- 2026-1: segundo semestre, mas la reprobada del periodo anterior ---
printf '  2026-1 (abierto)\n' >&2
reset_slots
ensure_section "$PLAN_TAP" TAP201 "$PER_OPEN" "$T_MAIN"  35 > /dev/null
ensure_section "$PLAN_TAP" TAP202 "$PER_OPEN" "$T_MAIN"  35 > /dev/null
ensure_section "$PLAN_TAP" TAP203 "$PER_OPEN" "$T_TWO"   35 > /dev/null
ensure_section "$PLAN_TAP" TAP204 "$PER_OPEN" "$T_TWO"   30 > /dev/null
ensure_section "$PLAN_TAP" TAP205 "$PER_OPEN" "$T_THREE" 30 > /dev/null
ensure_section "$PLAN_TAP" TAP206 "$PER_OPEN" "$T_THREE" 40 > /dev/null
# La que el estudiante reprobo en 2025-2 se vuelve a ofrecer
ensure_section "$PLAN_TAP" TAP105 "$PER_OPEN" "$T_MAIN"  35 > /dev/null
# Algo de oferta en las otras dos carreras, para que no esten vacias. El
# reparto de bloques NO se reinicia: los docentes se comparten entre planes y
# volver al bloque 1 los haria chocar consigo mismos.
for code in TAD101 TAD102; do
    ensure_section "$PLAN_TAD" "$code" "$PER_OPEN" "$T_THREE" 30 > /dev/null
done
for code in TCS101 TCS102; do
    ensure_section "$PLAN_TCS" "$code" "$PER_OPEN" "$T_TWO" 30 > /dev/null
done

# ---------------------------------------------------------------
# Matricula
# ---------------------------------------------------------------

banner "MATRICULA"

ensure_matriculation() { # studentId periodoId etiqueta
    local current
    current=$(api GET "/student-period-status?studentId=$1&academicPeriodId=$2" |
        "$PY" -c 'import sys,json
try: print(json.load(sys.stdin).get("enrollmentStatus",""))
except Exception: print("")')
    if [ "$current" = "MATRICULATED" ]; then
        skip "matricula $3"
        return
    fi
    if api PUT /student-period-status \
        "{\"studentId\":$1,\"academicPeriodId\":$2,\"enrollmentStatus\":\"MATRICULATED\"}" | grep -q '"id"'; then
        ok "matricula $3"
    else
        err "matricula $3"
    fi
}

[ "$past_status" = "ABIERTO" ] && ensure_matriculation "$S_MAIN" "$PER_PAST" "Pedro en 2025-2"
ensure_matriculation "$S_MAIN" "$PER_OPEN" "Pedro en 2026-1"
ensure_matriculation "$S_TWO" "$PER_OPEN" "Camila en 2026-1"
ensure_matriculation "$S_THREE" "$PER_OPEN" "Diego en 2026-1"

# ---------------------------------------------------------------
# Historial: inscribir, calificar y cerrar 2025-2
# ---------------------------------------------------------------

banner "HISTORIAL DE 2025-2"

if [ "$past_status" != "ABIERTO" ]; then
    skip "2025-2 ya esta cerrado, el historial existe"
else
    # Inscripcion: va con el token del estudiante y en un solo lote
    enrolled=$(api GET "/enrollments/me" '' "$STUDENT_TOKEN" | "$PY" -c 'import sys,json
try: data=json.load(sys.stdin)
except Exception: data=[]
print(len([e for e in data if str(e.get("academicPeriodId"))==sys.argv[1] and e.get("status")=="INSCRITA"]))' "$PER_PAST")

    if [ "$enrolled" -gt 0 ]; then
        skip "Pedro ya tiene $enrolled inscripciones en 2025-2"
    else
        section_ids=$(api GET "/enrollments/available?academicPeriodId=$PER_PAST" '' "$STUDENT_TOKEN" |
            "$PY" -c 'import sys,json
data=json.load(sys.stdin)
print(",".join(str(s["sectionId"]) for s in data[:6]))')
        if [ -z "$section_ids" ]; then
            err "no hay oferta disponible para Pedro en 2025-2"
        else
            cuantas=$(printf '%s\n' "$section_ids" | tr ',' '\n' | grep -c .)
            if api POST /enrollments \
                "{\"academicPeriodId\":$PER_PAST,\"sectionIds\":[$section_ids]}" "$STUDENT_TOKEN" | grep -q '"id"'; then
                ok "Pedro inscribio $cuantas asignaturas en 2025-2"
            else
                err "la inscripcion de Pedro fue rechazada"
            fi
        fi
    fi

    # Calificacion: la escribe el docente responsable, con su token.
    # Se reprueba Comunicacion Efectiva (TAP105), que no es prerrequisito de
    # nada: asi el historial muestra un REPROBADO sin bloquear el semestre 2.
    while read -r enrollment_id code value; do
        response=$(api POST /grades "{\"enrollmentId\":$enrollment_id,\"value\":$value}" "$TEACHER_TOKEN")
        if printf '%s' "$response" | grep -q '"id"'; then
            ok "nota $code = $value"
        elif printf '%s' "$response" | grep -q "already has a final grade"; then
            skip "nota $code ya registrada"
        else
            err "nota $code"
        fi
    done < <(api GET "/enrollments/student/$S_MAIN" | "$PY" -c 'import sys,json
# La nota de cada asignatura se decide aqui, junto con el id de su inscripcion
notas = {"TAP101": "6.2", "TAP102": "4.8", "TAP103": "5.9",
         "TAP104": "6.5", "TAP105": "3.4", "TAP106": "5.1"}
for e in json.load(sys.stdin):
    if str(e.get("academicPeriodId")) == sys.argv[1] and e.get("status") == "INSCRITA":
        print(e["id"], e["subjectCode"], notas.get(e["subjectCode"], "5.5"))' "$PER_PAST")

    # Cierre: consolida el historial y deja el periodo CERRADO, sin retorno
    response=$(api POST "/academic-closure/$PER_PAST")
    if printf '%s' "$response" | grep -q '"status":"CERRADO"'; then
        printf '%s' "$response" | "$PY" -c 'import sys,json
d=json.load(sys.stdin)
print("   + cierre de 2025-2: %d consolidadas, %d aprobadas, %d reprobadas" % (
    d["consolidatedEnrollments"], d["approvedCount"], d["failedCount"]))'
        created=$((created + 1))
    else
        err "cierre de 2025-2 -> $(printf '%s' "$response" | "$PY" -c 'import sys,json
try: print(json.load(sys.stdin).get("message",""))
except Exception: print("sin respuesta")')"
    fi
fi

# ---------------------------------------------------------------
# Resumen
# ---------------------------------------------------------------

banner "RESUMEN"
printf '   creados: %d | ya existian: %d | con error: %d\n\n' "$created" "$existing" "$failed"

api GET /careers | "$PY" -c 'import sys,json;print("   carreras    :",len(json.load(sys.stdin)))'
api GET /subjects | "$PY" -c 'import sys,json;print("   asignaturas :",len(json.load(sys.stdin)))'
api GET /teachers | "$PY" -c 'import sys,json;print("   docentes    :",len(json.load(sys.stdin)))'
api GET /students | "$PY" -c 'import sys,json;print("   estudiantes :",len(json.load(sys.stdin)))'
api GET /sections | "$PY" -c 'import sys,json;print("   secciones   :",len(json.load(sys.stdin)))'
api GET "/academic-history/student/$S_MAIN" | "$PY" -c 'import sys,json
data=json.load(sys.stdin)
ap=[r for r in data if r["result"]=="APROBADO"]
print("   historial de Pedro: %d registros (%d aprobados, %d reprobados)" % (len(data),len(ap),len(data)-len(ap)))'

cat <<'TXT'

Para la demostracion:
   admin@siga.cl    todo el catalogo, la matricula y el cierre de 2026-1
   teacher@siga.cl  sus secciones y el acta de las que tiene a cargo
   student@siga.cl  historial de 2025-2 e inscripcion abierta en 2026-1

Detalle a tener presente en la inscripcion de 2026-1: TAP105 se reprobo y se
volvio a ofrecer, asi que la seleccion tiene que incluirla. Es la regla de no
inscribir un semestre superior mientras quede una pendiente mas abajo, y la
pantalla lo explica en el resumen.
TXT

[ "$failed" -gt 0 ] && exit 1
exit 0
