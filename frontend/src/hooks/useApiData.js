import { useCallback, useEffect, useState } from 'react';

/**
 * Una lectura al montar, con su loading, su error normalizado y recarga.
 *
 * Es el companero de useApiResource (colecciones con CRUD) para las pantallas
 * de solo lectura: historial, mis secciones, acta de una seccion.
 *
 * El fetcher tiene que ser estable, por eso la pagina lo envuelve en useCallback:
 *   const load = useCallback(() => academicHistoryService.mine(), []);
 *   const history = useApiData(load);
 */
export function useApiData(fetcher, { auto = true } = {}) {
    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(Boolean(auto));
    const [error, setError] = useState(null);

    const reload = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const result = await fetcher();
            setData(result);
            return result;
        } catch (apiError) {
            setError(apiError);
            return null;
        } finally {
            setLoading(false);
        }
    }, [fetcher]);

    useEffect(() => {
        if (auto) reload();
    }, [auto, reload]);

    return { data, loading, error, setError, reload };
}
