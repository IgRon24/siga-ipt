import { http } from '../api/client';
import { endpoints } from '../api/endpoints';
import { isNotFoundError } from '../utils/httpError';

/** TeacherController. El ACTIVO se exige al asignar una seccion, no despues. */
export const teacherService = {
    list: (params) => http.get(endpoints.teachers, { params }),
    get: (id) => http.get(endpoints.teacher(id)),

    /**
     * El docente del token, resuelto por el backend con el claim "email".
     * Un 404 no es un error que mostrar: significa que el usuario tiene el rol
     * TEACHER pero no existe su ficha de docente, y de eso avisa la pantalla.
     */
    me: () =>
        http.get(endpoints.teacherMe).catch((error) => {
            if (isNotFoundError(error)) return null;
            throw error;
        }),
    create: (payload) => http.post(endpoints.teachers, payload),
    update: (id, payload) => http.put(endpoints.teacher(id), payload),
    changeStatus: (id, status) => http.patch(endpoints.teacherStatus(id), null, { params: { status } }),
    remove: (id) => http.del(endpoints.teacher(id)),
};
