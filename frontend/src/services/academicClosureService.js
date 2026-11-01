import { http } from '../api/client';
import { endpoints } from '../api/endpoints';

/**
 * AcademicClosureController (solo ADMIN). Devuelve el resumen del cierre:
 * {consolidatedEnrollments, approvedCount, failedCount, graduatedStudentRuns,
 * eliminatedStudentRuns}. Si falta alguna nota responde 409 con los codigos de
 * las asignaturas pendientes.
 */
export const academicClosureService = {
    close: (academicPeriodId) => http.post(endpoints.closure(academicPeriodId)),
};
