import { useCallback, useState } from 'react';

/**
 * Una llamada puntual (un submit, un cierre de periodo, una nota) con su
 * loading y su error ya normalizado. Expone fieldErrors para los formularios.
 */
export function useApiCall(fn) {
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);

    const call = useCallback(
        async (...args) => {
            setLoading(true);
            setError(null);
            try {
                const data = await fn(...args);
                return { ok: true, data, error: null };
            } catch (apiError) {
                setError(apiError);
                return { ok: false, data: null, error: apiError };
            } finally {
                setLoading(false);
            }
        },
        [fn],
    );

    return {
        call,
        loading,
        error,
        fieldErrors: error?.fieldErrors ?? {},
        reset: () => setError(null),
    };
}
