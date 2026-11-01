import { http } from '../api/client';
import { endpoints } from '../api/endpoints';

/**
 * EnrollmentController. La inscripcion es por lote: se envian todas las
 * secciones juntas porque el minimo de 3 y el maximo de 6 solo se pueden
 * validar sobre el conjunto. El estudiante sale del token, nunca del body.
 *
 * cancel() no borra: deja la inscripcion en ANULADA.
 */
export const enrollmentService = {
    available: (academicPeriodId, studentId) =>
        http.get(endpoints.availableSections, { params: { academicPeriodId, studentId } }),
    enroll: (academicPeriodId, sectionIds) =>
        http.post(endpoints.enrollments, { academicPeriodId, sectionIds }),
    mine: () => http.get(endpoints.myEnrollments),
    byStudent: (studentId) => http.get(endpoints.enrollmentsByStudent(studentId)),
    cancel: (id) => http.del(endpoints.enrollment(id)),
};
