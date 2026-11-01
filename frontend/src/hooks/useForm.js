import { useCallback, useState } from 'react';
import { runValidations } from '../utils/validators';

/**
 * Estado de un formulario con una sola bolsa de errores por campo: ahi caen
 * tanto las validaciones locales (rules) como el mapa que devuelve el backend
 * en los 400, que es exactamente la misma forma {campo: mensaje}.
 *
 *   const form = useForm({ code: '' }, { rules: { code: [required] } });
 *   <TextField label="Codigo" {...form.fieldProps('code')} />
 */
export function useForm(initialValues, { rules } = {}) {
    const [values, setValues] = useState(initialValues);
    const [errors, setErrors] = useState({});

    const setValue = useCallback((name, value) => {
        setValues((current) => ({ ...current, [name]: value }));
        // Al editar, el error de ese campo deja de aplicar
        setErrors((current) => (current[name] ? { ...current, [name]: undefined } : current));
    }, []);

    const handleChange = useCallback(
        (event) => {
            const { name, value, type, checked } = event.target;
            setValue(name, type === 'checkbox' ? checked : value);
        },
        [setValue],
    );

    const reset = useCallback(
        (next = initialValues) => {
            setValues(next);
            setErrors({});
        },
        [initialValues],
    );

    const validate = useCallback(() => {
        if (!rules) return true;
        const found = runValidations(values, rules);
        setErrors(found);
        return Object.keys(found).length === 0;
    }, [rules, values]);

    /** Los errores por campo del 400 del backend. */
    const applyFieldErrors = useCallback((fieldErrors) => {
        setErrors((current) => ({ ...current, ...fieldErrors }));
    }, []);

    const fieldProps = useCallback(
        (name) => ({
            name,
            value: values[name] ?? '',
            onChange: handleChange,
            error: errors[name],
        }),
        [values, errors, handleChange],
    );

    return { values, setValues, setValue, errors, setErrors, handleChange, reset, validate, applyFieldErrors, fieldProps };
}
