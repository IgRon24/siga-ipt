import { useCallback, useMemo } from 'react';
import { useApiData } from './useApiData';
import { academicPeriodService } from '../services/academicPeriodService';
import { sectionService } from '../services/sectionService';
import { teacherService } from '../services/teacherService';

/**
 * El docente del token con sus secciones y el estado de cada periodo.
 *
 * Lo comparten "Mis secciones" y "Calificaciones": las dos necesitan resolver
 * al docente del token, pedir sus secciones y saber si el periodo esta ABIERTO,
 * porque de eso depende si las notas se pueden tocar.
 *
 * El docente lo resuelve el backend en GET /api/teachers/me a partir del claim
 * "email", igual que /api/students/me para el estudiante.
 *
 * El estado del periodo no viene en SectionResponseDTO (solo el id y el codigo),
 * de ahi el mapa aparte contra GET /api/academic-periods.
 */
export function useTeacherSections() {
    const loadTeacher = useCallback(() => teacherService.me(), []);
    const teacher = useApiData(loadTeacher);

    const loadPeriods = useCallback(() => academicPeriodService.list(), []);
    const periods = useApiData(loadPeriods);

    const teacherId = teacher.data?.id;
    const loadSections = useCallback(
        () => (teacherId ? sectionService.byTeacher(teacherId) : Promise.resolve([])),
        [teacherId],
    );
    const sections = useApiData(loadSections);

    const periodStatusById = useMemo(
        () => new Map((periods.data ?? []).map((period) => [period.id, period.status])),
        [periods.data],
    );

    const reload = useCallback(() => {
        teacher.reload();
        periods.reload();
        sections.reload();
    }, [teacher, periods, sections]);

    return {
        teacher: teacher.data,
        /** El usuario tiene rol TEACHER pero no existe como docente en el sistema. */
        teacherMissing: !teacher.loading && !teacher.error && !teacher.data,
        sections: sections.data ?? [],
        periods: periods.data ?? [],
        periodStatusById,
        isOpenPeriod: (section) => periodStatusById.get(section?.academicPeriodId) === 'ABIERTO',
        loading: teacher.loading || sections.loading,
        error: teacher.error ?? sections.error ?? periods.error,
        reload,
        reloadSections: sections.reload,
    };
}
