import { http } from '../api/client';
import { endpoints } from '../api/endpoints';

/**
 * AcademicPeriodController. changeStatus a CERRADO delega en el cierre de
 * periodo: puede responder 409 con las asignaturas sin nota. Para cerrar con
 * el resumen del cierre, usar academicClosureService.close().
 *
 * closureReadiness es ese mismo diagnostico sin ejecutar nada: devuelve
 * {canClose, enrolledCount, gradedCount, pendingCount, pendingSubjectCodes,
 * sections[]}, donde pendingSubjectCodes es la lista que daria el 409.
 */
export const academicPeriodService = {
    list: (params) => http.get(endpoints.periods, { params }),
    get: (id) => http.get(endpoints.period(id)),
    create: (payload) => http.post(endpoints.periods, payload),
    update: (id, payload) => http.put(endpoints.period(id), payload),
    changeStatus: (id, status) => http.patch(endpoints.periodStatus(id), null, { params: { status } }),
    closureReadiness: (id) => http.get(endpoints.periodClosureReadiness(id)),
    remove: (id) => http.del(endpoints.period(id)),
};
