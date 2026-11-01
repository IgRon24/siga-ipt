/**
 * Los enums del backend viajan en espanol (ACTIVA, VIGENTE, INSCRITA...) y la
 * interfaz tambien esta en espanol, pero las etiquetas y los colores se
 * definen una sola vez aqui para que ninguna pantalla los invente.
 *
 * color usa la paleta de MUI: default | primary | success | warning | error | info
 */

export const CAREER_STATUS = {
    ACTIVA: { label: 'Activa', color: 'success' },
    INACTIVA: { label: 'Inactiva', color: 'default' },
};

export const STUDY_PLAN_STATUS = {
    VIGENTE: { label: 'Vigente', color: 'success' },
    NO_VIGENTE: { label: 'No vigente', color: 'default' },
};

export const STUDENT_STATUS = {
    REGULAR: { label: 'Regular', color: 'success' },
    POSTERGACION: { label: 'Postergacion', color: 'warning' },
    RETIRO_TEMPORAL: { label: 'Retiro temporal', color: 'warning' },
    EGRESADO: { label: 'Egresado', color: 'info' },
    ELIMINADO: { label: 'Eliminado', color: 'error' },
};

/** Los dos ultimos los asigna el cierre de periodo, nunca el usuario. */
export const MANUAL_STUDENT_STATUSES = ['REGULAR', 'POSTERGACION', 'RETIRO_TEMPORAL'];

export const TEACHER_STATUS = {
    ACTIVO: { label: 'Activo', color: 'success' },
    INACTIVO: { label: 'Inactivo', color: 'default' },
};

export const PERIOD_STATUS = {
    ABIERTO: { label: 'Abierto', color: 'success' },
    CERRADO: { label: 'Cerrado', color: 'default' },
};

export const ENROLLMENT_STATUS = {
    INSCRITA: { label: 'Inscrita', color: 'primary' },
    ANULADA: { label: 'Anulada', color: 'default' },
};

export const MATRICULATION_STATUS = {
    MATRICULATED: { label: 'Matriculado', color: 'success' },
    NOT_MATRICULATED: { label: 'No matriculado', color: 'default' },
};

export const ACADEMIC_RESULT = {
    APROBADO: { label: 'Aprobado', color: 'success' },
    REPROBADO: { label: 'Reprobado', color: 'error' },
};

/** Catalogos agrupados: <StatusChip kind="student" value="REGULAR" /> */
export const STATUS_CATALOGS = {
    career: CAREER_STATUS,
    studyPlan: STUDY_PLAN_STATUS,
    student: STUDENT_STATUS,
    teacher: TEACHER_STATUS,
    period: PERIOD_STATUS,
    enrollment: ENROLLMENT_STATUS,
    matriculation: MATRICULATION_STATUS,
    result: ACADEMIC_RESULT,
};

/** Opciones listas para un <SelectField>. */
export function statusOptions(catalog, only) {
    return Object.entries(catalog)
        .filter(([value]) => !only || only.includes(value))
        .map(([value, meta]) => ({ value, label: meta.label }));
}
