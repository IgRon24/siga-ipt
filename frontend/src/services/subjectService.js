import { http } from '../api/client';
import { endpoints } from '../api/endpoints';

/** SubjectController y PrerequisiteController (los prerrequisitos son un subrecurso). */
export const subjectService = {
    list: () => http.get(endpoints.subjects),
    get: (id) => http.get(endpoints.subject(id)),
    byStudyPlan: (studyPlanId) => http.get(endpoints.subjectsByStudyPlan(studyPlanId)),
    create: (payload) => http.post(endpoints.subjects, payload),
    update: (id, payload) => http.put(endpoints.subject(id), payload),
    remove: (id) => http.del(endpoints.subject(id)),

    listPrerequisites: (subjectId) => http.get(endpoints.prerequisites(subjectId)),
    addPrerequisite: (subjectId, prerequisiteSubjectId) =>
        http.post(endpoints.prerequisites(subjectId), { prerequisiteSubjectId }),
    removePrerequisite: (subjectId, prerequisiteSubjectId) =>
        http.del(endpoints.prerequisite(subjectId, prerequisiteSubjectId)),
};
