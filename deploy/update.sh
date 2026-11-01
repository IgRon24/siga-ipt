#!/usr/bin/env bash
#
# Actualiza la demo desplegada a las imagenes que estan en Docker Hub.
#
# Lo usa el job "deploy" de .github/workflows/ci.yml despues de publicar las
# imagenes, y sirve igual a mano desde la instancia:
#
#   cd ~/siga-ipt && git pull && bash deploy/update.sh
#
# No toca la base de datos ni los volumenes: solo reemplaza contenedores por la
# imagen nueva. Los datos de la demo sobreviven a cualquier despliegue.
#
# El reemplazo va REPLICA POR REPLICA, esperando que cada una quede healthy
# antes de tocar la siguiente. Asi nginx siempre tiene al menos una sana a la
# que mandar trafico y la demo no se cae durante el despliegue: es el beneficio
# concreto de tener replicas, no solo un requisito de la rubrica.

set -euo pipefail

cd "$(dirname "$0")/.."

# Las tres replicas viven en el compose principal + el override.
export COMPOSE_FILE=${COMPOSE_FILE:-docker-compose.yml:docker-compose.three.yml}

# Espera a que un contenedor quede healthy. Los que no declaran healthcheck
# (nginx, frontend) solo tienen que estar corriendo.
wait_healthy() {
    local name=$1 tries=${2:-60}
    for _ in $(seq "$tries"); do
        local state health
        state=$(docker inspect -f '{{.State.Status}}' "$name" 2>/dev/null || echo missing)
        health=$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$name" 2>/dev/null || echo none)

        if [ "$state" = "running" ] && { [ "$health" = "healthy" ] || [ "$health" = "none" ]; }; then
            printf '   %s -> %s/%s\n' "$name" "$state" "$health"
            return 0
        fi
        sleep 2
    done
    printf '   %s NO quedo sano (%s/%s)\n' "$name" "${state:-?}" "${health:-?}" >&2
    return 1
}

echo "== Imagenes =================================================="
docker compose pull

echo ""
echo "== Reemplazo replica por replica ============================="
# --no-deps evita que arrastre a db/keycloak: solo se recrea la replica.
for replica in backend-1 backend-2 backend-3 frontend-1 frontend-2; do
    if ! docker compose config --services | grep -qx "$replica"; then
        continue        # el compose de dos replicas no tiene backend-3
    fi
    echo "   recreando $replica..."
    docker compose up -d --no-build --no-deps "$replica"
    wait_healthy "siga-$replica"
done

echo ""
echo "== El resto del stack ========================================"
# Recoge cualquier cambio en nginx, keycloak o la base (normalmente, nada).
docker compose up -d --no-build

echo ""
echo "== Verificacion =============================================="
for _ in $(seq 30); do
    if curl -fsS --max-time 5 localhost/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
        echo "   /actuator/health -> UP"
        code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 localhost/api/careers)
        echo "   /api/careers sin token -> $code (se espera 401)"
        echo ""
        docker compose ps
        exit 0
    fi
    sleep 2
done

echo "   el stack no respondio UP" >&2
docker compose ps >&2
exit 1
