/**
 * Normalizador de errores del backend.
 *
 * El GlobalExceptionHandler responde {timestamp, status, error, message}, pero
 * "message" es un string en los 404/409 y un mapa campo -> mensaje en los 400
 * de validacion. Los 401/403 los escribe SecurityConfig con otra forma todavia
 * ({error, message} en espanol). Aqui todo eso se convierte en una sola forma:
 *
 *   { status, code, message, fieldErrors }
 *
 * Es la unica traduccion de errores de la aplicacion: los formularios leen
 * fieldErrors y las pantallas muestran message.
 */

export const ERROR_CODES = {
    VALIDATION: 'VALIDATION_ERROR',
    BUSINESS: 'BUSINESS_RULE_VIOLATION',
    NOT_FOUND: 'RESOURCE_NOT_FOUND',
    UNAUTHORIZED: 'UNAUTHORIZED',
    FORBIDDEN: 'FORBIDDEN',
    NETWORK: 'NETWORK_ERROR',
    UNKNOWN: 'UNKNOWN_ERROR',
};

const DEFAULT_MESSAGES = {
    400: 'La solicitud tiene datos invalidos.',
    401: 'Tu sesion no es valida o expiro.',
    403: 'No tienes permisos para esta accion.',
    404: 'El recurso no existe.',
    409: 'La operacion no cumple una regla de negocio.',
    500: 'Error interno del servidor.',
};

export function normalizeError(error) {
    // Sin respuesta: el backend no esta arriba o la red fallo
    if (!error?.response) {
        return {
            status: 0,
            code: ERROR_CODES.NETWORK,
            message: 'No se pudo conectar con el servidor.',
            fieldErrors: {},
        };
    }

    const { status, data } = error.response;
    const code = data?.error ?? codeForStatus(status);

    // 400: message es el mapa de errores por campo
    if (data?.message && typeof data.message === 'object') {
        return {
            status,
            code: ERROR_CODES.VALIDATION,
            message: 'Revisa los campos marcados.',
            fieldErrors: data.message,
        };
    }

    return {
        status,
        code,
        message: typeof data?.message === 'string' ? data.message : messageForStatus(status),
        fieldErrors: {},
    };
}

function codeForStatus(status) {
    if (status === 401) return ERROR_CODES.UNAUTHORIZED;
    if (status === 403) return ERROR_CODES.FORBIDDEN;
    return ERROR_CODES.UNKNOWN;
}

function messageForStatus(status) {
    return DEFAULT_MESSAGES[status] ?? 'Ocurrio un error inesperado.';
}

export const isValidationError = (error) => error?.code === ERROR_CODES.VALIDATION;
export const isBusinessError = (error) => error?.code === ERROR_CODES.BUSINESS;
export const isNotFoundError = (error) => error?.code === ERROR_CODES.NOT_FOUND;
