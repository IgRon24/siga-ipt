import { http } from '../api/client';
import { endpoints } from '../api/endpoints';

/**
 * StudentController. El plan de estudios no se envia: el backend asigna el
 * VIGENTE de la carrera. EGRESADO y ELIMINADO los pone el cierre de periodo.
 */
export const studentService = {
    list: (params) => http.get(endpoints.students, { params }),
    get: (id) => http.get(endpoints.student(id)),
    me: () => http.get(endpoints.studentMe),
    create: (payload) => http.post(endpoints.students, payload),
    update: (id, payload) => http.put(endpoints.student(id), payload),
    changeStatus: (id, status) => http.patch(endpoints.studentStatus(id), null, { params: { status } }),
    remove: (id) => http.del(endpoints.student(id)),
};
