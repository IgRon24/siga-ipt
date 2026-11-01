import { http } from '../api/client';
import { endpoints } from '../api/endpoints';

/**
 * StudyPlanController. Ojo con la regla: dejar un plan VIGENTE degrada al
 * anterior de la misma carrera, asi que despues de create/update/changeStatus
 * conviene recargar la lista.
 */
export const studyPlanService = {
    list: () => http.get(endpoints.studyPlans),
    get: (id) => http.get(endpoints.studyPlan(id)),
    byCareer: (careerId) => http.get(endpoints.studyPlansByCareer(careerId)),
    create: (payload) => http.post(endpoints.studyPlans, payload),
    update: (id, payload) => http.put(endpoints.studyPlan(id), payload),
    changeStatus: (id, status) => http.patch(endpoints.studyPlanStatus(id), null, { params: { status } }),
    remove: (id) => http.del(endpoints.studyPlan(id)),
};
