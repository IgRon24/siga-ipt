// SIGA IPT - Prueba de carga con k6.
//
// Responde la pregunta "a cuantos usuarios deja de responder": en el modo por
// defecto (ramp) sube la tasa de peticiones por escalones y ABORTA en cuanto se
// rompe uno de los umbrales (p95 de latencia o tasa de error). El escalon en el
// que aborta es la capacidad del sistema tal como esta configurado.
//
// Necesita perf/users.json, que escribe perf/seed-users.ps1.
// Como correrlo esta en perf/README.md.
//
//   MODE      ramp (defecto) | soak | smoke | write
//   MAX_RPS   techo del escalonado          (defecto 120)
//   RPS       tasa fija en soak             (defecto 30)
//   STAGE     duracion de cada escalon      (defecto 45s)
//   P95_MS    umbral de latencia            (defecto 1500)
//   SIGA_URL  base de la API y de Keycloak  (defecto, el del manifest)

import http from 'k6/http';
import { check, fail, group } from 'k6';
import { Counter } from 'k6/metrics';

// open() solo existe en el contexto de inicializacion: el manifest se lee aqui.
const manifest = JSON.parse(open('./users.json'));

const BASE = __ENV.SIGA_URL || manifest.baseUrl;
const KC = __ENV.SIGA_URL ? `${__ENV.SIGA_URL}/auth` : manifest.keycloakUrl;
const REALM = manifest.realm;
const CLIENT_ID = __ENV.KC_CLIENT_ID || manifest.clientId;
const PERIOD_ID = manifest.periodId;
const USERS = manifest.users;
const PASSWORD = __ENV.PERF_USER_PASS;

const MAX_RPS = Number(__ENV.MAX_RPS || 120);
const RPS = Number(__ENV.RPS || 30);
const STAGE = __ENV.STAGE || '45s';
const P95_MS = Number(__ENV.P95_MS || 1500);
const MODE = __ENV.MODE || 'ramp';

// Peticiones que fallaron por token vencido y se reintentaron: si este contador
// sube mucho, el access token del realm dura menos que la prueba.
const reauth = new Counter('reauth_total');

const TOKEN_URL = `${KC}/realms/${REALM}/protocol/openid-connect/token`;

// Escalones: 10% del techo hasta el techo, en 10 pasos iguales.
function rampStages() {
    const stages = [];
    for (let step = 1; step <= 10; step += 1) {
        stages.push({ target: Math.max(1, Math.round((MAX_RPS * step) / 10)), duration: STAGE });
    }
    return stages;
}

const scenarios = {
    // Sube la carga hasta encontrar el punto de quiebre.
    ramp: {
        executor: 'ramping-arrival-rate',
        startRate: 1,
        timeUnit: '1s',
        preAllocatedVUs: Math.min(USERS.length, 50),
        maxVUs: Math.max(50, MAX_RPS * 2),
        stages: rampStages(),
        exec: 'studentSession',
    },
    // Tasa fija: sirve para ver si aguanta sostenido lo que aguanto un rato.
    soak: {
        executor: 'constant-arrival-rate',
        rate: RPS,
        timeUnit: '1s',
        duration: __ENV.DURATION || '5m',
        preAllocatedVUs: Math.min(USERS.length, 50),
        maxVUs: Math.max(50, RPS * 2),
        exec: 'studentSession',
    },
    // Una vuelta sola: comprueba que el escenario y las credenciales sirven.
    smoke: {
        executor: 'shared-iterations',
        vus: 1,
        iterations: 1,
        exec: 'studentSession',
    },
    // Escritura: PUT idempotente de la matricula, con el token del ADMIN.
    write: {
        executor: 'ramping-arrival-rate',
        startRate: 1,
        timeUnit: '1s',
        preAllocatedVUs: 10,
        maxVUs: Math.max(20, MAX_RPS),
        stages: rampStages(),
        exec: 'matriculationWrite',
    },
};

export const options = {
    scenarios: { [MODE]: scenarios[MODE] },
    thresholds: {
        // Abortar en el escalon que rompe el sistema: eso es la respuesta.
        http_req_failed: [{ threshold: 'rate<0.01', abortOnFail: true, delayAbortEval: '15s' }],
        http_req_duration: [
            { threshold: `p(95)<${P95_MS}`, abortOnFail: true, delayAbortEval: '15s' },
        ],
    },
    // Cada peticion lleva su nombre en el tag "name": el resumen sale por endpoint.
    summaryTrendStats: ['avg', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

function login(username) {
    const res = http.post(
        TOKEN_URL,
        {
            grant_type: 'password',
            client_id: CLIENT_ID,
            username,
            password: PASSWORD,
        },
        { tags: { name: 'kc:token' } },
    );
    if (res.status !== 200) {
        fail(`login de ${username} fallo con ${res.status}: ${res.body}`);
    }
    return res.json('access_token');
}

/**
 * Los tokens se piden una sola vez, antes de medir: la prueba mide la API, no
 * a Keycloak. Si la prueba dura mas que el access token del realm (5 min por
 * defecto), cada VU renueva el suyo al primer 401 y lo cuenta en reauth_total.
 */
export function setup() {
    if (!PASSWORD) {
        fail('Falta PERF_USER_PASS: es la password de los usuarios de prueba.');
    }
    if (USERS.length === 0) {
        fail('users.json no tiene usuarios: corre antes perf/seed-users.ps1.');
    }

    const tokens = USERS.map((user) => ({ username: user.username, token: login(user.username) }));

    let adminToken = null;
    if (MODE === 'write') {
        if (!__ENV.KC_ADMIN_USER || !__ENV.KC_ADMIN_PASS) {
            fail('El modo write necesita KC_ADMIN_USER y KC_ADMIN_PASS.');
        }
        const res = http.post(TOKEN_URL, {
            grant_type: 'password',
            client_id: CLIENT_ID,
            username: __ENV.KC_ADMIN_USER,
            password: __ENV.KC_ADMIN_PASS,
        });
        if (res.status !== 200) fail(`login del ADMIN fallo con ${res.status}`);
        adminToken = res.json('access_token');
    }

    return { tokens, adminToken, studentIds: USERS.map((u) => u.studentId) };
}

// Token del VU, renovado si vence a mitad de la prueba.
let current = null;

function pick(data) {
    if (!current) {
        current = data.tokens[(__VU - 1) % data.tokens.length];
    }
    return current;
}

function authGet(path, token, name) {
    const params = { headers: { Authorization: `Bearer ${token}` }, tags: { name } };
    let res = http.get(`${BASE}${path}`, params);

    // 401 = el token vencio: se renueva una vez y se reintenta.
    if (res.status === 401) {
        reauth.add(1);
        current.token = login(current.username);
        params.headers.Authorization = `Bearer ${current.token}`;
        res = http.get(`${BASE}${path}`, params);
    }

    check(res, { [`${name} 200`]: (r) => r.status === 200 });
    return res;
}

/**
 * Una sesion del estudiante, que es la pantalla mas pesada del sistema: el
 * listado de secciones disponibles calcula prerrequisitos, cupos y choques de
 * horario por cada candidata.
 */
export function studentSession(data) {
    const user = pick(data);

    group('sesion del estudiante', () => {
        authGet('/api/students/me', user.token, 'students/me');
        authGet('/api/academic-periods?status=ABIERTO', user.token, 'periods');
        if (PERIOD_ID) {
            authGet(
                `/api/enrollments/available?academicPeriodId=${PERIOD_ID}`,
                user.token,
                'enrollments/available',
            );
        }
        authGet('/api/enrollments/me', user.token, 'enrollments/me');
        authGet('/api/academic-history/me', user.token, 'history/me');
    });
}

/**
 * Carga de escritura. La matricula es el unico write repetible del sistema: es
 * un PUT idempotente, asi que mil pasadas dejan la base igual que una. La
 * inscripcion no sirve para esto (cada estudiante solo puede inscribir una vez
 * por periodo) y la nota tampoco (una por inscripcion).
 */
export function matriculationWrite(data) {
    if (!PERIOD_ID) fail('El modo write necesita un periodo ABIERTO.');

    const studentId = data.studentIds[(__ITER + __VU) % data.studentIds.length];
    const res = http.put(
        `${BASE}/api/student-period-status`,
        JSON.stringify({
            studentId,
            academicPeriodId: PERIOD_ID,
            enrollmentStatus: 'MATRICULATED',
        }),
        {
            headers: {
                Authorization: `Bearer ${data.adminToken}`,
                'Content-Type': 'application/json',
            },
            tags: { name: 'student-period-status' },
        },
    );
    check(res, { 'matricula 200': (r) => r.status === 200 });
}
