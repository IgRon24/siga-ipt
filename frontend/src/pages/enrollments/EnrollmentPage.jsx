import { useCallback, useMemo, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Grid from '@mui/material/Grid';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import StatusChip from '../../components/domain/StatusChip';
import SelectField from '../../components/form/SelectField';
import ConfirmDialog from '../../components/ui/ConfirmDialog';
import EmptyState from '../../components/ui/EmptyState';
import ErrorAlert from '../../components/ui/ErrorAlert';
import Loader from '../../components/ui/Loader';
import PageHeader from '../../components/ui/PageHeader';
import { useApiCall } from '../../hooks/useApiCall';
import { useApiData } from '../../hooks/useApiData';
import { useToast } from '../../hooks/useToast';
import { academicPeriodService } from '../../services/academicPeriodService';
import { enrollmentService } from '../../services/enrollmentService';
import { studentPeriodStatusService } from '../../services/studentPeriodStatusService';
import { studentService } from '../../services/studentService';
import { evaluateSelection } from '../../utils/enrollmentRules';
import AvailableSectionsList from './AvailableSectionsList';
import CurrentEnrollments from './CurrentEnrollments';
import SelectionSummary from './SelectionSummary';

/**
 * Inscripcion academica del estudiante autenticado.
 *
 * Es una operacion por lote: se envian todas las secciones juntas en un POST,
 * porque el minimo de 3 y el maximo de 6 solo se pueden validar sobre el
 * conjunto. El estudiante nunca viaja en el cuerpo, sale del email del token.
 *
 * Solo se ofrecen periodos ABIERTO, y el envio se bloquea cuando el estudiante
 * no esta REGULAR o no esta matriculado en el periodo, que son las dos
 * condiciones que el backend exige antes de mirar las asignaturas.
 */
export default function EnrollmentPage() {
    const { showSuccess } = useToast();

    const loadStudent = useCallback(() => studentService.me(), []);
    const student = useApiData(loadStudent);

    const loadOpenPeriods = useCallback(() => academicPeriodService.list({ status: 'ABIERTO' }), []);
    const periods = useApiData(loadOpenPeriods);

    const [chosenPeriodId, setChosenPeriodId] = useState('');
    const [selectedIds, setSelectedIds] = useState([]);
    const [toCancel, setToCancel] = useState(null);

    const sortedPeriods = useMemo(
        () => [...(periods.data ?? [])].sort((a, b) => b.code.localeCompare(a.code)),
        [periods.data],
    );

    const loadMine = useCallback(() => enrollmentService.mine(), []);
    const mine = useApiData(loadMine);

    /**
     * El periodo por defecto se deriva, no se guarda en un efecto: el mas
     * reciente de los abiertos donde el estudiante ya tiene inscripciones (el
     * que esta cursando) y, si no tiene ninguna, el mas reciente abierto.
     */
    const defaultPeriodId = useMemo(() => {
        const withEnrollments = new Set((mine.data ?? []).map((enrollment) => enrollment.academicPeriodId));
        const cursando = sortedPeriods.find((item) => withEnrollments.has(item.id));
        const fallback = cursando ?? sortedPeriods[0];
        return fallback ? String(fallback.id) : '';
    }, [sortedPeriods, mine.data]);

    const periodId = chosenPeriodId || defaultPeriodId;
    const period = sortedPeriods.find((item) => String(item.id) === periodId) ?? null;

    const studentId = student.data?.id;

    const loadMatriculation = useCallback(
        () => (studentId && periodId ? studentPeriodStatusService.find(studentId, periodId) : Promise.resolve(null)),
        [studentId, periodId],
    );
    const matriculation = useApiData(loadMatriculation);

    const loadAvailable = useCallback(
        () => (periodId ? enrollmentService.available(periodId) : Promise.resolve([])),
        [periodId],
    );
    const available = useApiData(loadAvailable);

    const { call: submitEnrollment, error: submitError, loading: submitting, reset: resetSubmitError } = useApiCall(
        useCallback((sectionIds) => enrollmentService.enroll(Number(periodId), sectionIds), [periodId]),
    );

    const { call: submitCancel, error: cancelError, loading: cancelling, reset: resetCancelError } = useApiCall(
        useCallback((enrollmentId) => enrollmentService.cancel(enrollmentId), []),
    );

    const periodEnrollments = useMemo(
        () => (mine.data ?? []).filter((enrollment) => String(enrollment.academicPeriodId) === periodId),
        [mine.data, periodId],
    );
    const activeEnrollments = useMemo(
        () => periodEnrollments.filter((enrollment) => enrollment.status === 'INSCRITA'),
        [periodEnrollments],
    );

    const evaluation = useMemo(
        () =>
            evaluateSelection({
                available: available.data ?? [],
                current: activeEnrollments,
                selectedIds,
            }),
        [available.data, activeEnrollments, selectedIds],
    );

    // Las dos condiciones previas del backend, explicadas antes de intentar
    const notRegular = student.data && student.data.status !== 'REGULAR';
    const notMatriculated =
        Boolean(studentId) &&
        !matriculation.loading &&
        matriculation.data?.enrollmentStatus !== 'MATRICULATED';

    const disabledReason = notRegular
        ? `Tu estado es ${student.data.status}: solo un estudiante REGULAR puede inscribir asignaturas.`
        : notMatriculated
          ? `No estas matriculado en ${period?.code ?? 'el periodo'}. La matricula la registra un administrador y es requisito para inscribir.`
          : null;

    const handleToggle = (sectionId) => {
        setSelectedIds((current) =>
            current.includes(sectionId) ? current.filter((id) => id !== sectionId) : [...current, sectionId],
        );
        resetSubmitError();
    };

    const handlePeriodChange = (event) => {
        setChosenPeriodId(event.target.value);
        setSelectedIds([]);
        resetSubmitError();
    };

    const handleSubmit = async () => {
        const { ok, data } = await submitEnrollment(selectedIds);
        if (!ok) return;

        setSelectedIds([]);
        showSuccess(`${data.length} ${data.length === 1 ? 'asignatura inscrita' : 'asignaturas inscritas'}`);
        available.reload();
        mine.reload();
    };

    const handleCancel = async () => {
        const { ok } = await submitCancel(toCancel.id);
        if (!ok) return;

        setToCancel(null);
        showSuccess(`Inscripcion de ${toCancel.subjectCode} anulada`);
        available.reload();
        mine.reload();
    };

    if (student.loading || periods.loading) {
        return (
            <>
                <PageHeader title="Inscripcion academica" />
                <Loader label="Cargando tu informacion..." />
            </>
        );
    }

    return (
        <>
            <PageHeader
                title="Inscripcion academica"
                subtitle={
                    student.data
                        ? `${student.data.careerName} · plan ${student.data.studyPlanIdentifier}`
                        : 'Entre 3 y 6 asignaturas por periodo'
                }
                action={student.data && <StatusChip kind="student" value={student.data.status} />}
            />

            <ErrorAlert error={student.error} onRetry={student.reload} />
            <ErrorAlert error={periods.error} onRetry={periods.reload} />
            <ErrorAlert error={available.error} onRetry={available.reload} />
            <ErrorAlert error={mine.error} onRetry={mine.reload} />
            <ErrorAlert error={submitError} onClose={resetSubmitError} />

            {sortedPeriods.length === 0 ? (
                <Paper variant="outlined">
                    <EmptyState
                        title="No hay periodos abiertos"
                        description="La inscripcion se habilita cuando un administrador abre un periodo academico."
                    />
                </Paper>
            ) : (
                <>
                    <Box sx={{ maxWidth: 320, mb: 3 }}>
                        <SelectField
                            label="Periodo academico"
                            value={periodId}
                            onChange={handlePeriodChange}
                            options={sortedPeriods.map((item) => ({
                                value: String(item.id),
                                label: item.code,
                            }))}
                            helperText="Solo periodos abiertos"
                        />
                    </Box>

                    {disabledReason && (
                        <Alert severity="warning" sx={{ mb: 3 }}>
                            {disabledReason}
                        </Alert>
                    )}

                    <Grid container spacing={3}>
                        <Grid size={{ xs: 12, lg: 8 }}>
                            <Typography variant="subtitle1" gutterBottom>
                                Oferta disponible
                            </Typography>
                            <AvailableSectionsList
                                sections={available.data ?? []}
                                selectedIds={selectedIds}
                                onToggle={handleToggle}
                                loading={available.loading}
                                disabled={Boolean(disabledReason)}
                            />

                            <Typography variant="subtitle1" sx={{ mt: 4 }} gutterBottom>
                                Tus inscripciones en {period?.code}
                            </Typography>
                            <ErrorAlert error={cancelError} onClose={resetCancelError} />
                            <CurrentEnrollments
                                enrollments={periodEnrollments}
                                loading={mine.loading}
                                onCancel={setToCancel}
                                canCancel={!disabledReason}
                            />
                        </Grid>

                        <Grid size={{ xs: 12, lg: 4 }}>
                            <SelectionSummary
                                evaluation={evaluation}
                                enrolledCount={activeEnrollments.length}
                                onSubmit={handleSubmit}
                                submitting={submitting}
                                disabledReason={disabledReason}
                            />
                        </Grid>
                    </Grid>
                </>
            )}

            <ConfirmDialog
                open={Boolean(toCancel)}
                title="Anular inscripcion"
                message={`Se anulara ${toCancel?.subjectCode} — ${toCancel?.subjectName}. La inscripcion queda como ANULADA y el cupo se libera. No se puede anular si ya tiene nota.`}
                confirmLabel="Anular"
                loading={cancelling}
                error={cancelError}
                onConfirm={handleCancel}
                onClose={() => {
                    setToCancel(null);
                    resetCancelError();
                }}
            />
        </>
    );
}
