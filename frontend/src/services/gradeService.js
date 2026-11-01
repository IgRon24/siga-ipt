import { http } from '../api/client';
import { endpoints } from '../api/endpoints';

/**
 * GradeController. Las notas las escribe el docente responsable de la seccion
 * (se identifica por el email del token) y solo con el periodo ABIERTO. El
 * acta trae tambien las inscripciones sin nota, con value y result en null.
 */
export const gradeService = {
    sheet: (sectionId) => http.get(endpoints.gradeSheet(sectionId)),
    create: (enrollmentId, value) => http.post(endpoints.grades, { enrollmentId, value }),
    update: (id, enrollmentId, value) => http.put(endpoints.grade(id), { enrollmentId, value }),
};
