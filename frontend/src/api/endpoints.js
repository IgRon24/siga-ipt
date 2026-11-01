/**
 * Las rutas del backend en un solo lugar, con los mismos nombres que los
 * controllers de com.example.siga.controller. La baseURL (".../api") la pone
 * api/client.js, asi que aqui las rutas van sin el prefijo.
 */
export const endpoints = {
    // Carreras
    careers: '/careers',
    career: (id) => `/careers/${id}`,

    // Planes de estudio
    studyPlans: '/study-plans',
    studyPlan: (id) => `/study-plans/${id}`,
    studyPlansByCareer: (careerId) => `/study-plans/career/${careerId}`,
    studyPlanStatus: (id) => `/study-plans/${id}/status`,

    // Asignaturas y prerrequisitos
    subjects: '/subjects',
    subject: (id) => `/subjects/${id}`,
    subjectsByStudyPlan: (studyPlanId) => `/subjects/study-plan/${studyPlanId}`,
    prerequisites: (subjectId) => `/subjects/${subjectId}/prerequisites`,
    prerequisite: (subjectId, prerequisiteSubjectId) =>
        `/subjects/${subjectId}/prerequisites/${prerequisiteSubjectId}`,

    // Docentes
    teachers: '/teachers',
    teacher: (id) => `/teachers/${id}`,
    teacherMe: '/teachers/me',
    teacherStatus: (id) => `/teachers/${id}/status`,

    // Estudiantes
    students: '/students',
    student: (id) => `/students/${id}`,
    studentMe: '/students/me',
    studentStatus: (id) => `/students/${id}/status`,

    // Periodos academicos
    periods: '/academic-periods',
    period: (id) => `/academic-periods/${id}`,
    periodStatus: (id) => `/academic-periods/${id}/status`,
    periodClosureReadiness: (id) => `/academic-periods/${id}/closure-readiness`,

    // Secciones y horarios
    sections: '/sections',
    section: (id) => `/sections/${id}`,
    sectionSchedule: (id) => `/sections/${id}/schedule-blocks`,

    // Matricula por periodo
    matriculation: '/student-period-status',
    matriculationByPeriod: (academicPeriodId) => `/student-period-status/period/${academicPeriodId}`,
    matriculationByStudent: (studentId) => `/student-period-status/student/${studentId}`,

    // Inscripcion academica
    enrollments: '/enrollments',
    enrollment: (id) => `/enrollments/${id}`,
    availableSections: '/enrollments/available',
    myEnrollments: '/enrollments/me',
    enrollmentsByStudent: (studentId) => `/enrollments/student/${studentId}`,

    // Calificaciones
    grades: '/grades',
    grade: (id) => `/grades/${id}`,
    gradeSheet: (sectionId) => `/grades/section/${sectionId}`,

    // Cierre de periodo e historial
    closure: (periodId) => `/academic-closure/${periodId}`,
    myHistory: '/academic-history/me',
    historyByStudent: (studentId) => `/academic-history/student/${studentId}`,

    // Diagnostico del token
    me: '/me',
};
