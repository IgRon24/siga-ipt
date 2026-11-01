import axios from 'axios';
import { normalizeError } from '../utils/httpError';

/**
 * Unico lugar de la aplicacion que conoce axios.
 *
 * - Interceptor de request: adjunta el bearer en cada llamada.
 * - Interceptor de response: convierte cualquier error en la forma normalizada
 *   {status, code, message, fieldErrors} y avisa cuando el token ya no sirve,
 *   para que la sesion se cierre en un solo lugar.
 *
 * El token no se lee de aqui: AuthContext inyecta los callbacks con
 * configureAuth(), asi el cliente no depende de React ni del storage.
 */
export const api = axios.create({
    baseURL: import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api',
    headers: { 'Content-Type': 'application/json' },
});

let getToken = () => null;
let handleUnauthorized = () => {};

export function configureAuth(handlers) {
    if (handlers.getToken) getToken = handlers.getToken;
    if (handlers.onUnauthorized) handleUnauthorized = handlers.onUnauthorized;
}

api.interceptors.request.use((config) => {
    const token = getToken();
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

api.interceptors.response.use(
    (response) => response,
    (error) => {
        const normalized = normalizeError(error);
        if (normalized.status === 401) {
            handleUnauthorized(normalized);
        }
        return Promise.reject(normalized);
    },
);

/** Atajos: devuelven el body, que es lo unico que necesitan los services. */
export const http = {
    get: (url, config) => api.get(url, config).then((r) => r.data),
    post: (url, body, config) => api.post(url, body, config).then((r) => r.data),
    put: (url, body, config) => api.put(url, body, config).then((r) => r.data),
    patch: (url, body, config) => api.patch(url, body, config).then((r) => r.data),
    del: (url, config) => api.delete(url, config).then(() => undefined),
};
