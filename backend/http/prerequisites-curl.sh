#!/usr/bin/env bash
# SIGA IPT - Prueba manual de Epica 3: Asignaturas y prerrequisitos
#
# Uso (Git Bash):
#   1. Levanta la app:  ./mvnw spring-boot:run     (Postgres + Keycloak arriba)
#   2. Pega los tokens abajo, o exportalos antes de ejecutar:
#        export ADMIN_TOKEN="eyJ..."
#        export STUDENT_TOKEN="eyJ..."
#   3. bash http/prerequisites-curl.sh
#
# El script crea sus propios datos, imprime el status de cada peticion y
# marca OK/FALLA comparando con el status esperado.

BASE_URL="${BASE_URL:-http://localhost:8080}"
ADMIN_TOKEN="${ADMIN_TOKEN:-TU-TOKEN-ADMIN}"
STUDENT_TOKEN="${STUDENT_TOKEN:-TU-TOKEN-STUDENT}"

# --- Login automatico contra Keycloak (opcional) -----------------------
# Si no pegas los tokens arriba, el script los pide a Keycloak usando estas
# variables. Definelas como variables de entorno antes de ejecutar.
KC_URL="${KC_URL:-http://localhost:8081}"
KC_REALM="${KC_REALM:-siga-realm}"
KC_CLIENT_ID="${KC_CLIENT_ID:-}"
KC_CLIENT_SECRET="${KC_CLIENT_SECRET:-}"
KC_ADMIN_USER="${KC_ADMIN_USER:-}"
KC_ADMIN_PASS="${KC_ADMIN_PASS:-}"
KC_STUDENT_USER="${KC_STUDENT_USER:-}"
KC_STUDENT_PASS="${KC_STUDENT_PASS:-}"

get_token() { # <usuario> <password>
  local extra=()
  [ -n "$KC_CLIENT_SECRET" ] && extra+=(--data-urlencode "client_secret=$KC_CLIENT_SECRET")

  curl -s -X POST "$KC_URL/realms/$KC_REALM/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    --data-urlencode "grant_type=password" \
    --data-urlencode "client_id=$KC_CLIENT_ID" \
    --data-urlencode "username=$1" \
    --data-urlencode "password=$2" \
    "${extra[@]}" \
    | grep -o '"access_token":"[^"]*"' | head -n 1 | cut -d'"' -f4
}

if [ "$ADMIN_TOKEN" = "TU-TOKEN-ADMIN" ] && [ -n "$KC_ADMIN_USER" ]; then
  echo "Obteniendo token de ADMIN desde Keycloak..."
  ADMIN_TOKEN=$(get_token "$KC_ADMIN_USER" "$KC_ADMIN_PASS")
fi

if [ "$STUDENT_TOKEN" = "TU-TOKEN-STUDENT" ] && [ -n "$KC_STUDENT_USER" ]; then
  echo "Obteniendo token de STUDENT desde Keycloak..."
  STUDENT_TOKEN=$(get_token "$KC_STUDENT_USER" "$KC_STUDENT_PASS")
fi

if [ -z "$ADMIN_TOKEN" ] || [ "$ADMIN_TOKEN" = "TU-TOKEN-ADMIN" ]; then
  echo "ERROR: falta el token de ADMIN."
  echo "  Opcion 1: export ADMIN_TOKEN=\"eyJ...\""
  echo "  Opcion 2: export KC_CLIENT_ID / KC_ADMIN_USER / KC_ADMIN_PASS para el login automatico"
  exit 1
fi

# Sufijos para no chocar con datos de corridas anteriores (codigos unicos).
SUFFIX="${SUFFIX:-$(date +%H%M%S)}"
# El codigo de carrera debe ser exactamente 5 digitos (@Pattern del DTO).
CAREER_CODE="${CAREER_CODE:-1$(date +%M%S)}"

pass=0
fail=0

# req <descripcion> <status esperado> <metodo> <ruta> <token> [body]
req() {
  local desc="$1" expected="$2" method="$3" path="$4" token="$5" body="$6"
  local response status

  if [ -n "$body" ]; then
    response=$(curl -s -w $'\n%{http_code}' -X "$method" "$BASE_URL$path" \
      -H "Authorization: Bearer $token" \
      -H "Content-Type: application/json" \
      -d "$body")
  elif [ -n "$token" ]; then
    response=$(curl -s -w $'\n%{http_code}' -X "$method" "$BASE_URL$path" \
      -H "Authorization: Bearer $token")
  else
    response=$(curl -s -w $'\n%{http_code}' -X "$method" "$BASE_URL$path")
  fi

  status=$(printf '%s' "$response" | tail -n 1)
  BODY=$(printf '%s' "$response" | sed '$d')

  if [ "$status" = "$expected" ]; then
    printf '  [OK]    %-3s  %s\n' "$status" "$desc"
    pass=$((pass + 1))
  else
    printf '  [FALLA] %-3s  %s  (se esperaba %s)\n' "$status" "$desc" "$expected"
    printf '          %s\n' "$BODY"
    fail=$((fail + 1))
  fi
}

# Extrae el campo "id" del ultimo body recibido.
last_id() {
  printf '%s' "$BODY" | grep -o '"id":[0-9]*' | head -n 1 | cut -d: -f2
}

subject_body() { # <code> <name> <semester> <studyPlanId>
  cat <<JSON
{"code":"$1","name":"$2","semester":$3,"telTheory":2,"telExercises":2,"telLab":2,"sct":5,"studyPlanId":$4}
JSON
}

echo
echo "=== PREPARACION ==============================================="

req "Crear carrera" 201 POST /api/careers "$ADMIN_TOKEN" \
  "{\"code\":\"$CAREER_CODE\",\"name\":\"Tecnico Analista Programador\",\"description\":\"Carrera tecnica de cuatro semestres\",\"status\":\"ACTIVA\"}"
CAREER_ID=$(last_id)

req "Crear plan de estudios VIGENTE" 201 POST /api/study-plans "$ADMIN_TOKEN" \
  "{\"identifier\":\"P1-$SUFFIX\",\"name\":\"Plan de estudios 2021.3\",\"status\":\"VIGENTE\",\"careerId\":$CAREER_ID}"
PLAN_ID=$(last_id)

req "Crear TAP101 (semestre 1)" 201 POST /api/subjects "$ADMIN_TOKEN" \
  "$(subject_body "TAP101-$SUFFIX" "Programacion I" 1 "$PLAN_ID")"
TAP101=$(last_id)

req "Crear TAP201 (semestre 2)" 201 POST /api/subjects "$ADMIN_TOKEN" \
  "$(subject_body "TAP201-$SUFFIX" "Programacion II" 2 "$PLAN_ID")"
TAP201=$(last_id)

req "Crear TAP202 (semestre 2)" 201 POST /api/subjects "$ADMIN_TOKEN" \
  "$(subject_body "TAP202-$SUFFIX" "Bases de Datos" 2 "$PLAN_ID")"
TAP202=$(last_id)

req "Crear TAP102 (semestre 1)" 201 POST /api/subjects "$ADMIN_TOKEN" \
  "$(subject_body "TAP102-$SUFFIX" "Matematica I" 1 "$PLAN_ID")"
TAP102=$(last_id)

req "Crear TAP103 (semestre 1)" 201 POST /api/subjects "$ADMIN_TOKEN" \
  "$(subject_body "TAP103-$SUFFIX" "Ingles I" 1 "$PLAN_ID")"
TAP103=$(last_id)

req "Crear TAP104 (semestre 1)" 201 POST /api/subjects "$ADMIN_TOKEN" \
  "$(subject_body "TAP104-$SUFFIX" "Algebra I" 1 "$PLAN_ID")"
TAP104=$(last_id)

echo
echo "=== CASOS FELICES ============================================="

req "TAP101 (sem 1) como prerrequisito de TAP201 (sem 2)" 201 \
  POST "/api/subjects/$TAP201/prerequisites" "$ADMIN_TOKEN" \
  "{\"prerequisiteSubjectId\":$TAP101}"

req "Listar prerrequisitos de TAP201" 200 \
  GET "/api/subjects/$TAP201/prerequisites" "$ADMIN_TOKEN"

req "Listar prerrequisitos de TAP101 (vacio)" 200 \
  GET "/api/subjects/$TAP101/prerequisites" "$ADMIN_TOKEN"

echo
echo "=== CASOS DE RECHAZO =========================================="

req "Prerrequisito duplicado" 409 \
  POST "/api/subjects/$TAP201/prerequisites" "$ADMIN_TOKEN" \
  "{\"prerequisiteSubjectId\":$TAP101}"

req "Prerrequisito del mismo semestre" 409 \
  POST "/api/subjects/$TAP201/prerequisites" "$ADMIN_TOKEN" \
  "{\"prerequisiteSubjectId\":$TAP202}"

req "Prerrequisito de semestre posterior" 409 \
  POST "/api/subjects/$TAP101/prerequisites" "$ADMIN_TOKEN" \
  "{\"prerequisiteSubjectId\":$TAP201}"

req "Asignatura prerrequisito de si misma" 409 \
  POST "/api/subjects/$TAP201/prerequisites" "$ADMIN_TOKEN" \
  "{\"prerequisiteSubjectId\":$TAP201}"

req "Prerrequisito inexistente" 404 \
  POST "/api/subjects/$TAP201/prerequisites" "$ADMIN_TOKEN" \
  '{"prerequisiteSubjectId":999999}'

req "Asignatura inexistente" 404 \
  POST "/api/subjects/999999/prerequisites" "$ADMIN_TOKEN" \
  "{\"prerequisiteSubjectId\":$TAP101}"

req "Body sin prerequisiteSubjectId" 400 \
  POST "/api/subjects/$TAP201/prerequisites" "$ADMIN_TOKEN" '{}'

req "No borrar TAP101: es prerrequisito de TAP201" 409 \
  DELETE "/api/subjects/$TAP101" "$ADMIN_TOKEN"

echo
echo "=== REGLA DE OTRO PLAN DE ESTUDIOS ============================"

req "Crear segundo plan NO_VIGENTE" 201 POST /api/study-plans "$ADMIN_TOKEN" \
  "{\"identifier\":\"P2-$SUFFIX\",\"name\":\"Plan de estudios 2022.3\",\"status\":\"NO_VIGENTE\",\"careerId\":$CAREER_ID}"
PLAN_ID_2=$(last_id)

req "Crear asignatura en el otro plan" 201 POST /api/subjects "$ADMIN_TOKEN" \
  "$(subject_body "OTR101-$SUFFIX" "Asignatura de otro plan" 1 "$PLAN_ID_2")"
OTHER_SUBJECT=$(last_id)

req "Prerrequisito de otro plan de estudios" 409 \
  POST "/api/subjects/$TAP201/prerequisites" "$ADMIN_TOKEN" \
  "{\"prerequisiteSubjectId\":$OTHER_SUBJECT}"

echo
echo "=== LIMITE DE 3 PRERREQUISITOS ================================"

req "Segundo prerrequisito de TAP201" 201 \
  POST "/api/subjects/$TAP201/prerequisites" "$ADMIN_TOKEN" \
  "{\"prerequisiteSubjectId\":$TAP102}"

req "Tercer prerrequisito de TAP201 (limite permitido)" 201 \
  POST "/api/subjects/$TAP201/prerequisites" "$ADMIN_TOKEN" \
  "{\"prerequisiteSubjectId\":$TAP103}"

req "Cuarto prerrequisito de TAP201" 409 \
  POST "/api/subjects/$TAP201/prerequisites" "$ADMIN_TOKEN" \
  "{\"prerequisiteSubjectId\":$TAP104}"

echo
echo "=== SEGURIDAD ================================================="

req "STUDENT puede leer prerrequisitos" 200 \
  GET "/api/subjects/$TAP201/prerequisites" "$STUDENT_TOKEN"

req "STUDENT no puede crear prerrequisitos" 403 \
  POST "/api/subjects/$TAP201/prerequisites" "$STUDENT_TOKEN" \
  "{\"prerequisiteSubjectId\":$TAP104}"

req "STUDENT no puede borrar prerrequisitos" 403 \
  DELETE "/api/subjects/$TAP201/prerequisites/$TAP101" "$STUDENT_TOKEN"

req "Sin token" 401 \
  GET "/api/subjects/$TAP201/prerequisites" ""

echo
echo "=== BORRADO ==================================================="

req "ADMIN borra el prerrequisito TAP101 de TAP201" 204 \
  DELETE "/api/subjects/$TAP201/prerequisites/$TAP101" "$ADMIN_TOKEN"

req "Borrar un prerrequisito que ya no existe" 404 \
  DELETE "/api/subjects/$TAP201/prerequisites/$TAP101" "$ADMIN_TOKEN"

req "TAP101 ya se puede borrar" 204 \
  DELETE "/api/subjects/$TAP101" "$ADMIN_TOKEN"

req "TAP201 quedo con 2 prerrequisitos" 200 \
  GET "/api/subjects/$TAP201/prerequisites" "$ADMIN_TOKEN"
echo "          $BODY"

echo
echo "==============================================================="
printf '  %s OK, %s FALLA\n' "$pass" "$fail"
echo "==============================================================="
echo

[ "$fail" -eq 0 ]
