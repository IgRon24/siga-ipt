import { http } from '../api/client';
import { endpoints } from '../api/endpoints';

/**
 * SectionController. replaceSchedule reemplaza el horario completo (no agrega)
 * y recibe la lista de bloques tal cual: [{day, module}, ...].
 */
export const sectionService = {
    list: (params) => http.get(endpoints.sections, { params }),
    get: (id) => http.get(endpoints.section(id)),
    byPeriod: (academicPeriodId) => http.get(endpoints.sections, { params: { academicPeriodId } }),
    byTeacher: (teacherId) => http.get(endpoints.sections, { params: { teacherId } }),
    create: (payload) => http.post(endpoints.sections, payload),
    update: (id, payload) => http.put(endpoints.section(id), payload),
    replaceSchedule: (id, scheduleBlocks) => http.post(endpoints.sectionSchedule(id), scheduleBlocks),
    remove: (id) => http.del(endpoints.section(id)),
};
