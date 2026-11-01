/** Formateo para mostrar. Nada de esto se envia al backend tal cual. */

/** 12345678-9 -> 12.345.678-9 */
export function formatRun(run) {
    if (!run) return '';
    const [digits, checkDigit] = run.split('-');
    if (!checkDigit) return run;
    return `${digits.replace(/\B(?=(\d{3})+(?!\d))/g, '.')}-${checkDigit.toUpperCase()}`;
}

/** La escala es 1.0 a 7.0 con un decimal; el backend ya redondea al guardar. */
export function formatGrade(value) {
    if (value === null || value === undefined || value === '') return '-';
    return Number(value).toFixed(1);
}

/** Horas TEL de una asignatura: "2-2-0". */
export function formatTel(subject) {
    if (!subject) return '';
    return [subject.telTheory, subject.telExercises, subject.telLab].join('-');
}

export function formatDateTime(value) {
    if (!value) return '-';
    const date = new Date(value);
    return Number.isNaN(date.getTime())
        ? '-'
        : date.toLocaleString('es-CL', { dateStyle: 'short', timeStyle: 'short' });
}

export function formatDate(value) {
    if (!value) return '-';
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? '-' : date.toLocaleDateString('es-CL', { dateStyle: 'medium' });
}

/** "2026-1" -> "2026, 1er semestre" */
export function formatPeriod(period) {
    if (!period) return '';
    const semester = period.semester === 1 ? '1er' : '2do';
    return `${period.year}, ${semester} semestre`;
}

/**
 * Lo mismo, pero partiendo del codigo. El historial academico copia el codigo
 * del periodo como parte de su snapshot y no trae el anio ni el semestre.
 */
export function formatPeriodCode(code) {
    if (!code) return '';
    const [year, semester] = code.split('-');
    if (!semester) return code;
    return formatPeriod({ year, semester: Number(semester) });
}

export function initialsOf(fullName) {
    if (!fullName) return '?';
    return fullName
        .split(' ')
        .filter(Boolean)
        .slice(0, 2)
        .map((part) => part[0].toUpperCase())
        .join('');
}
