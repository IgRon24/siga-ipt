/**
 * Validaciones de cliente. Son las mismas reglas que las anotaciones Jakarta
 * del backend y existen solo para avisar antes de enviar: la validacion que
 * manda es la del servidor, que vuelve en fieldErrors.
 */

export const RUN_PATTERN = /^\d{7,8}-[\dkK]$/;
export const CAREER_CODE_PATTERN = /^\d{5}$/;
export const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export const MIN_GRADE = 1.0;
export const MAX_GRADE = 7.0;
export const PASSING_GRADE = 4.0;

export const MIN_SUBJECTS_PER_PERIOD = 3;
export const MAX_SUBJECTS_PER_PERIOD = 6;

export const MIN_SECTION_CAPACITY = 1;
export const MAX_SECTION_CAPACITY = 60;

export function required(value, message = 'Este campo es obligatorio') {
    const empty = value === null || value === undefined || String(value).trim() === '';
    return empty ? message : null;
}

export function validRun(value) {
    if (!value) return null;
    return RUN_PATTERN.test(value) ? null : 'El RUN debe tener el formato 12345678-9';
}

export function validCareerCode(value) {
    if (!value) return null;
    return CAREER_CODE_PATTERN.test(value) ? null : 'El codigo debe ser un numero de 5 digitos';
}

export function validEmail(value) {
    if (!value) return null;
    return EMAIL_PATTERN.test(value) ? null : 'El email no es valido';
}

export function inRange(value, min, max, message) {
    if (value === null || value === undefined || value === '') return null;
    const number = Number(value);
    if (Number.isNaN(number) || number < min || number > max) {
        return message ?? `El valor debe estar entre ${min} y ${max}`;
    }
    return null;
}

export function validGrade(value) {
    return inRange(value, MIN_GRADE, MAX_GRADE, 'La nota debe estar entre 1.0 y 7.0');
}

/** Regla del backend: las horas TEL suman menos de 8. */
export function validTelHours({ telTheory, telExercises, telLab }) {
    const total = Number(telTheory || 0) + Number(telExercises || 0) + Number(telLab || 0);
    return total < 8 ? null : 'Las horas TEL deben sumar menos de 8';
}

/** Corre un mapa {campo: [validadores]} y devuelve {campo: primerError}. */
export function runValidations(values, rules) {
    const errors = {};
    Object.entries(rules).forEach(([field, validators]) => {
        for (const validate of validators) {
            const error = validate(values[field], values);
            if (error) {
                errors[field] = error;
                break;
            }
        }
    });
    return errors;
}
