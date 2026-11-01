import { http } from '../api/client';
import { endpoints } from '../api/endpoints';

/**
 * StudentPeriodStatusController: la matricula del estudiante en un periodo.
 * Es un PUT idempotente (registra o actualiza) y es requisito para inscribir.
 */
export const studentPeriodStatusService = {
    save: (payload) => http.put(endpoints.matriculation, payload),
    find: (studentId, academicPeriodId) =>
        http.get(endpoints.matriculation, { params: { studentId, academicPeriodId } }),
    byPeriod: (academicPeriodId) => http.get(endpoints.matriculationByPeriod(academicPeriodId)),
    byStudent: (studentId) => http.get(endpoints.matriculationByStudent(studentId)),
};
