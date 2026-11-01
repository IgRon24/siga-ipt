import { useCallback, useEffect, useMemo, useState } from 'react';

/**
 * Estado de una coleccion servida por un service con la forma
 * {list, create, update, remove}: carga, errores y recarga tras cada mutacion.
 *
 * Las mutaciones no lanzan: devuelven {ok, data, error} para que la pantalla
 * decida si muestra el 409 en un cartel o los fieldErrors en el formulario.
 *
 * Aqui el setState dentro de un efecto es el patron correcto (sincronizar con
 * un sistema externo, la API), por eso .oxlintrc.json apaga esa regla en
 * hooks/ y contexts/, los dos unicos lugares donde esto pasa.
 */
export function useApiResource(service, options = {}) {
    const { auto = true, params } = options;

    // Se compara por valor: asi un objeto literal nuevo en cada render no recarga
    const paramsKey = JSON.stringify(params ?? null);
    const resolvedParams = useMemo(() => JSON.parse(paramsKey) ?? undefined, [paramsKey]);

    const [items, setItems] = useState([]);
    const [loading, setLoading] = useState(Boolean(auto));
    const [error, setError] = useState(null);

    const reload = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const data = await service.list(resolvedParams);
            setItems(Array.isArray(data) ? data : []);
            return data;
        } catch (apiError) {
            setError(apiError);
            return null;
        } finally {
            setLoading(false);
        }
    }, [service, resolvedParams]);

    // Carga inicial y recarga cuando cambian los filtros
    useEffect(() => {
        if (auto) reload();
    }, [auto, reload]);

    const mutate = useCallback(
        async (operation) => {
            setError(null);
            try {
                const data = await operation();
                await reload();
                return { ok: true, data, error: null };
            } catch (apiError) {
                setError(apiError);
                return { ok: false, data: null, error: apiError };
            }
        },
        [reload],
    );

    return {
        items,
        loading,
        error,
        setError,
        reload,
        mutate,
        create: (payload) => mutate(() => service.create(payload)),
        update: (id, payload) => mutate(() => service.update(id, payload)),
        remove: (id) => mutate(() => service.remove(id)),
    };
}
