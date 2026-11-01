import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import { ERROR_CODES, isBusinessError, isValidationError } from '../../utils/httpError';

const TITLES = {
    [ERROR_CODES.BUSINESS]: 'La operacion no se puede realizar',
    [ERROR_CODES.VALIDATION]: 'Datos invalidos',
    [ERROR_CODES.NOT_FOUND]: 'No encontrado',
    [ERROR_CODES.FORBIDDEN]: 'Sin permisos',
    [ERROR_CODES.UNAUTHORIZED]: 'Sesion invalida',
    [ERROR_CODES.NETWORK]: 'Sin conexion con el servidor',
};

/**
 * Muestra un error ya normalizado. Las violaciones de regla de negocio (409)
 * son el caso frecuente de este sistema: no son fallas, son el backend
 * explicando por que no se puede, asi que se muestran como advertencia.
 */
export default function ErrorAlert({ error, onRetry, onClose, sx }) {
    if (!error) return null;

    const severity = isBusinessError(error) || isValidationError(error) ? 'warning' : 'error';
    const fields = Object.entries(error.fieldErrors ?? {});

    return (
        <Alert
            severity={severity}
            onClose={onClose}
            sx={{ mb: 2, ...sx }}
            action={
                onRetry ? (
                    <Button color="inherit" size="small" onClick={onRetry}>
                        Reintentar
                    </Button>
                ) : undefined
            }
        >
            <AlertTitle>{TITLES[error.code] ?? 'Error'}</AlertTitle>
            {error.message}
            {fields.length > 0 && (
                <Box component="ul" sx={{ pl: 2.5, mb: 0, mt: 1 }}>
                    {fields.map(([field, message]) => (
                        <li key={field}>
                            <strong>{field}</strong>: {message}
                        </li>
                    ))}
                </Box>
            )}
        </Alert>
    );
}
