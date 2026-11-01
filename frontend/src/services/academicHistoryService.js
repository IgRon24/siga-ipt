import { http } from '../api/client';
import { endpoints } from '../api/endpoints';

/**
 * AcademicHistoryController: solo lectura. El historial lo escribe unicamente
 * el cierre de periodo, y es el snapshot que decide que esta aprobado.
 */
export const academicHistoryService = {
    mine: () => http.get(endpoints.myHistory),
    byStudent: (studentId) => http.get(endpoints.historyByStudent(studentId)),
};
