/**
 * Autenticacion contra Keycloak.
 *
 * Usa el grant "password" (direct access grant) contra el token endpoint del
 * realm, que es el mismo flujo que usan los scripts de prueba del backend
 * (backend/http/*.ps1), asi que no exige configurar redirect URIs. Si mas
 * adelante se cambia a keycloak-js con PKCE, este archivo es el unico que
 * cambia: el resto de la aplicacion solo ve la forma "session".
 *
 * session = { accessToken, refreshToken, expiresAt, email, username, fullName, roles }
 */

const KC_URL = import.meta.env.VITE_KC_URL ?? 'http://localhost:8081';
const REALM = import.meta.env.VITE_KC_REALM ?? 'siga-realm';
const CLIENT_ID = import.meta.env.VITE_KC_CLIENT_ID ?? 'siga-frontend';
const CLIENT_SECRET = import.meta.env.VITE_KC_CLIENT_SECRET ?? '';

const TOKEN_URL = `${KC_URL}/realms/${REALM}/protocol/openid-connect/token`;
const LOGOUT_URL = `${KC_URL}/realms/${REALM}/protocol/openid-connect/logout`;

const STORAGE_KEY = 'siga.session';

/** Los roles de la aplicacion; el token trae tambien los internos de Keycloak. */
export const APP_ROLES = ['ADMIN', 'TEACHER', 'STUDENT'];

export async function login(username, password) {
    return requestToken({ grant_type: 'password', username, password });
}

export async function refresh(refreshToken) {
    return requestToken({ grant_type: 'refresh_token', refresh_token: refreshToken });
}

export async function logout(session) {
    clearSession();
    if (!session?.refreshToken) return;
    try {
        await fetch(LOGOUT_URL, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: formBody({ refresh_token: session.refreshToken }),
        });
    } catch {
        // Cerrar sesion local es lo que importa; si Keycloak no responde, da igual
    }
}

async function requestToken(extraParams) {
    const response = await fetch(TOKEN_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: formBody(extraParams),
    });

    const data = await response.json().catch(() => ({}));

    if (!response.ok) {
        throw {
            status: response.status,
            code: data.error ?? 'AUTH_ERROR',
            message: translateAuthError(data.error, data.error_description),
            fieldErrors: {},
        };
    }

    return toSession(data);
}

function formBody(extraParams) {
    const params = new URLSearchParams({ client_id: CLIENT_ID, ...extraParams });
    if (CLIENT_SECRET) params.set('client_secret', CLIENT_SECRET);
    return params.toString();
}

function toSession(data) {
    const claims = decodeClaims(data.access_token);
    return {
        accessToken: data.access_token,
        refreshToken: data.refresh_token,
        expiresAt: Date.now() + (data.expires_in ?? 300) * 1000,
        email: claims.email ?? '',
        username: claims.preferred_username ?? '',
        fullName: claims.name ?? claims.preferred_username ?? '',
        roles: (claims.realm_access?.roles ?? []).filter((role) => APP_ROLES.includes(role)),
    };
}

/** Lee el payload del JWT; el backend es quien valida la firma. */
export function decodeClaims(token) {
    try {
        const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
        return JSON.parse(decodeURIComponent(escape(atob(payload))));
    } catch {
        return {};
    }
}

/** Con 30 s de margen, para no usar un token que expira en pleno viaje. */
export function isExpired(session, skewMs = 30_000) {
    return !session?.expiresAt || session.expiresAt - skewMs <= Date.now();
}

export function loadSession() {
    try {
        const raw = sessionStorage.getItem(STORAGE_KEY);
        return raw ? JSON.parse(raw) : null;
    } catch {
        return null;
    }
}

export function saveSession(session) {
    try {
        sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    } catch {
        // Modo privado o storage bloqueado: la sesion vive solo en memoria
    }
}

export function clearSession() {
    try {
        sessionStorage.removeItem(STORAGE_KEY);
    } catch {
        // nada que limpiar
    }
}

function translateAuthError(error, description) {
    if (error === 'invalid_grant') return 'Usuario o contrasena incorrectos.';
    if (error === 'invalid_client') return 'El client de Keycloak no esta configurado correctamente.';
    if (error === 'unauthorized_client') return 'El client no permite este tipo de login.';
    return description ?? 'No se pudo iniciar sesion.';
}
