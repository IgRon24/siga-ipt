import { MAX_SUBJECTS_PER_PERIOD, MIN_SUBJECTS_PER_PERIOD } from './validators';
import { blockKey, blockLabel } from './schedule';

/**
 * Las reglas de la inscripcion, en funciones puras.
 *
 * Son las mismas de EnrollmentServiceImpl y se evaluan en el cliente para
 * explicar la seleccion antes de enviarla, no para reemplazar al backend: la
 * decision final siempre es el 409 del servidor, que ademas valida lo que aqui
 * no se puede ver (estado del estudiante, matricula, prerrequisitos).
 *
 * La oferta de GET /api/enrollments/available ya viene filtrada por el backend
 * (sin aprobadas, sin inscritas, con prerrequisitos cumplidos), asi que es
 * exactamente la lista de candidatas con la que el servidor va a comparar.
 */
export function evaluateSelection({ available = [], current = [], selectedIds = [] }) {
    const selected = available.filter((section) => selectedIds.includes(section.sectionId));

    const totalSubjects = current.length + selected.length;
    const totalSct = selected.reduce((sum, section) => sum + (section.subjectSct ?? 0), 0);

    // Si quedan menos de 3 disponibles, el minimo baja a lo que haya
    const minimum = Math.min(MIN_SUBJECTS_PER_PERIOD, current.length + available.length);

    const blockers = [];

    if (totalSubjects > MAX_SUBJECTS_PER_PERIOD) {
        blockers.push(
            `No puedes inscribir mas de ${MAX_SUBJECTS_PER_PERIOD} asignaturas en el periodo (llevarias ${totalSubjects}).`,
        );
    }

    if (selected.length > 0 && totalSubjects < minimum) {
        blockers.push(
            `Debes inscribir al menos ${minimum} ${minimum === 1 ? 'asignatura' : 'asignaturas'} en el periodo (llevarias ${totalSubjects}).`,
        );
    }

    selected
        .filter((section) => (section.availableSeats ?? 0) <= 0)
        .forEach((section) => {
            blockers.push(`La seccion de ${section.subjectCode} no tiene cupos disponibles.`);
        });

    scheduleConflicts(selected, current).forEach((conflict) => blockers.push(conflict));
    lowerSemesterPending(selected, available, selectedIds).forEach((pending) => blockers.push(pending));

    const unique = [...new Set(blockers)];

    return {
        selected,
        totalSubjects,
        totalSct,
        minimum,
        blockers: unique,
        canSubmit: selected.length > 0 && unique.length === 0,
    };
}

/** Choques de dia y modulo: dentro de la seleccion y contra lo ya inscrito. */
function scheduleConflicts(selected, current) {
    const owners = new Map();
    const conflicts = [];

    const entries = [
        ...current.map((enrollment) => ({
            code: enrollment.subjectCode,
            blocks: enrollment.scheduleBlocks ?? [],
            enrolled: true,
        })),
        ...selected.map((section) => ({
            code: section.subjectCode,
            blocks: section.scheduleBlocks ?? [],
            enrolled: false,
        })),
    ];

    entries.forEach(({ code, blocks, enrolled }) => {
        blocks.forEach((block) => {
            const key = blockKey(block);
            const owner = owners.get(key);
            if (owner && owner.code !== code) {
                conflicts.push(
                    `Choque el ${blockLabel(block)} entre ${owner.code}${owner.enrolled ? ' (ya inscrita)' : ''} y ${code}${enrolled ? ' (ya inscrita)' : ''}.`,
                );
            } else {
                owners.set(key, { code, enrolled });
            }
        });
    });

    return conflicts;
}

/** No se inscribe un semestre superior mientras quede una pendiente mas abajo. */
function lowerSemesterPending(selected, available, selectedIds) {
    return selected.flatMap((section) => {
        const pending = available.find(
            (candidate) =>
                candidate.subjectSemester < section.subjectSemester && !selectedIds.includes(candidate.sectionId),
        );
        return pending
            ? [
                  `No puedes inscribir ${section.subjectCode} (semestre ${section.subjectSemester}) mientras ${pending.subjectCode} (semestre ${pending.subjectSemester}) siga disponible.`,
              ]
            : [];
    });
}
