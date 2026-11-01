import { http } from '../api/client';
import { endpoints } from '../api/endpoints';

/** CareerController. Escrituras solo ADMIN; lecturas los tres roles. */
export const careerService = {
    list: () => http.get(endpoints.careers),
    get: (id) => http.get(endpoints.career(id)),
    create: (payload) => http.post(endpoints.careers, payload),
    update: (id, payload) => http.put(endpoints.career(id), payload),
    remove: (id) => http.del(endpoints.career(id)),
};
