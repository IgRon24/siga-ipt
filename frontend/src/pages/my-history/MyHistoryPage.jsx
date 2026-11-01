import { useCallback, useMemo } from 'react';
import Paper from '@mui/material/Paper';
import StatusChip from '../../components/domain/StatusChip';
import EmptyState from '../../components/ui/EmptyState';
import ErrorAlert from '../../components/ui/ErrorAlert';
import Loader from '../../components/ui/Loader';
import PageHeader from '../../components/ui/PageHeader';
import { useApiData } from '../../hooks/useApiData';
import { academicHistoryService } from '../../services/academicHistoryService';
import { studentService } from '../../services/studentService';
import { formatRun } from '../../utils/formatters';
import HistorySummary from './HistorySummary';
import PeriodHistoryCard from './PeriodHistoryCard';

/**
 * Historial academico del estudiante autenticado (GET /api/academic-history/me,
 * que resuelve al estudiante por el claim email del token).
 *
 * Es informacion inmutable: la escribe solo el cierre de periodo, con el codigo
 * y el nombre de la asignatura copiados en ese momento. De ahi que la pantalla
 * sea de lectura y se agrupe por periodo, de mas reciente a mas antiguo.
 */
export default function MyHistoryPage() {
    // Fetchers estables: useApiData recarga cuando cambia la referencia
    const loadHistory = useCallback(() => academicHistoryService.mine(), []);
    const loadProfile = useCallback(() => studentService.me(), []);

    const history = useApiData(loadHistory);
    const profile = useApiData(loadProfile);

    // Se memoriza para que la lista mantenga su identidad entre renders
    const records = useMemo(() => history.data ?? [], [history.data]);
    const summary = useMemo(() => summarize(records), [records]);
    const periods = useMemo(() => groupByPeriod(records), [records]);

    const student = profile.data;

    return (
        <>
            <PageHeader
                title="Mi historial academico"
                subtitle={
                    student
                        ? `${student.careerName} · plan ${student.studyPlanIdentifier} · RUN ${formatRun(student.run)}`
                        : 'Asignaturas de los periodos ya cerrados'
                }
                action={student && <StatusChip kind="student" value={student.status} />}
            />

            <ErrorAlert error={history.error} onRetry={history.reload} />

            {history.loading ? (
                <Loader label="Cargando historial..." />
            ) : records.length === 0 ? (
                <Paper variant="outlined">
                    <EmptyState
                        title="Todavia no tienes historial"
                        description="El historial se escribe cuando se cierra el periodo academico: ahi cada inscripcion calificada pasa a ser un registro permanente."
                    />
                </Paper>
            ) : (
                <>
                    <HistorySummary {...summary} />
                    {periods.map((period) => (
                        <PeriodHistoryCard key={period.code} period={period} />
                    ))}
                </>
            )}
        </>
    );
}

function summarize(records) {
    const approved = records.filter((record) => record.result === 'APROBADO').length;
    return {
        total: records.length,
        approved,
        failed: records.length - approved,
        average: averageGrade(records),
    };
}

/** Un grupo por periodo, del mas reciente al mas antiguo. */
function groupByPeriod(records) {
    const byCode = new Map();

    records.forEach((record) => {
        const code = record.academicPeriodCode;
        if (!byCode.has(code)) byCode.set(code, []);
        byCode.get(code).push(record);
    });

    return [...byCode.entries()]
        .sort(([a], [b]) => b.localeCompare(a))
        .map(([code, periodRecords]) => {
            const sorted = [...periodRecords].sort((a, b) => a.subjectCode.localeCompare(b.subjectCode));
            const approved = sorted.filter((record) => record.result === 'APROBADO').length;
            return {
                code,
                records: sorted,
                approved,
                failed: sorted.length - approved,
                average: averageGrade(sorted),
            };
        });
}

function averageGrade(records) {
    const grades = records.map((record) => Number(record.grade)).filter((grade) => !Number.isNaN(grade));
    if (grades.length === 0) return null;
    return grades.reduce((total, grade) => total + grade, 0) / grades.length;
}
