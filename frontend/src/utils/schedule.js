/**
 * Reglas de horario, iguales a las de SectionServiceImpl: cada bloque son 2
 * horas pedagogicas, la cantidad de bloques de una seccion es (T + E + L) / 2,
 * no se repiten bloques y un docente no puede tener dos secciones en el mismo
 * dia y modulo dentro del periodo.
 *
 * Se replican en el cliente para avisar antes de enviar y para dibujar la
 * grilla; el rechazo definitivo lo da el backend con un 409.
 */

export const HOURS_PER_BLOCK = 2;

export const DAYS = [
    { value: 'L', label: 'Lunes', short: 'Lun' },
    { value: 'M', label: 'Martes', short: 'Mar' },
    { value: 'W', label: 'Miercoles', short: 'Mie' },
    { value: 'J', label: 'Jueves', short: 'Jue' },
    { value: 'V', label: 'Viernes', short: 'Vie' },
];

export const MODULES = [1, 2, 3, 4, 5, 6];

export const dayLabel = (day) => DAYS.find((d) => d.value === day)?.label ?? day;

export const dayShort = (day) => DAYS.find((d) => d.value === day)?.short ?? day;

const DAY_ORDER = DAYS.map((day) => day.value);

/** Horario en orden de lectura: por dia de la semana y despues por modulo. */
export function sortBlocks(blocks = []) {
    return [...blocks].sort(
        (a, b) => DAY_ORDER.indexOf(a.day) - DAY_ORDER.indexOf(b.day) || a.module - b.module,
    );
}

export const blockKey = (block) => `${block.day}-${block.module}`;

export const blockLabel = (block) => `${dayLabel(block.day)}, modulo ${block.module}`;

/** Cuantos bloques exige la asignatura por sus horas TEL. */
export function requiredBlocks(subject) {
    if (!subject) return 0;
    const total = Number(subject.telTheory || 0) + Number(subject.telExercises || 0) + Number(subject.telLab || 0);
    return Math.floor(total / HOURS_PER_BLOCK);
}

export function hasDuplicates(blocks = []) {
    return new Set(blocks.map(blockKey)).size !== blocks.length;
}

/** Bloques repetidos entre dos horarios, para avisar de un choque. */
export function overlappingBlocks(blocksA = [], blocksB = []) {
    const keys = new Set(blocksA.map(blockKey));
    return blocksB.filter((block) => keys.has(blockKey(block)));
}

/** Valida un horario completo contra la asignatura. Devuelve null si esta bien. */
export function validateSchedule(subject, blocks = []) {
    const needed = requiredBlocks(subject);
    if (needed === 0) return 'La asignatura no tiene horas TEL, no puede tener horario';
    if (blocks.length !== needed) return `El horario debe tener ${needed} bloques y tiene ${blocks.length}`;
    if (hasDuplicates(blocks)) return 'Hay bloques repetidos en el horario';
    return null;
}

/** Secciones a grilla dia/modulo: grid['L'][1] = seccion. */
export function toScheduleGrid(sections = []) {
    const grid = {};
    DAYS.forEach(({ value }) => {
        grid[value] = {};
    });
    sections.forEach((section) => {
        (section.scheduleBlocks ?? []).forEach((block) => {
            if (grid[block.day]) grid[block.day][block.module] = section;
        });
    });
    return grid;
}
