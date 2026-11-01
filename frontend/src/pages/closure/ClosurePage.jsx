import { useCallback, useMemo, useState } from 'react';
import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import SelectField from '../../components/form/SelectField';
import ConfirmDialog from '../../components/ui/ConfirmDialog';
import EmptyState from '../../components/ui/EmptyState';
import ErrorAlert from '../../components/ui/ErrorAlert';
import Loader from '../../components/ui/Loader';
import PageHeader from '../../components/ui/PageHeader';
import { useApiCall } from '../../hooks/useApiCall';
import { useApiData } from '../../hooks/useApiData';
import { academicClosureService } from '../../services/academicClosureService';
import { academicPeriodService } from '../../services/academicPeriodService';
import ClosureReadiness from './ClosureReadiness';
import ClosureResult from './ClosureResult';

/**
 * Cierre de periodo academico (solo ADMIN).
 *
 * Es la unica via por la que un periodo queda CERRADO y no tiene retorno:
 * copia cada inscripcion calificada al historial y recalcula los estados
 * terminales de los estudiantes (ELIMINADO por segunda reprobacion de la misma
 * asignatura, que gana sobre EGRESADO por plan completo).
 *
 * Antes de ofrecer el boton la pantalla pide el diagnostico del backend
 * (GET /api/academic-periods/{id}/closure-readiness): canClose, el conteo de
 * notas pendientes y las asignaturas que bloquean el cierre, que son las mismas
 * que listaria el 409. El diagnostico lo calcula el mismo servicio que cierra,
 * asi que no hay dos versiones de la regla ni una llamada por seccion.
 */
export default function ClosurePage() {
    const loadOpenPeriods = useCallback(() => academicPeriodService.list({ status: 'ABIERTO' }), []);
    const periods = useApiData(loadOpenPeriods);

    const [chosenPeriodId, setChosenPeriodId] = useState('');
    const [confirming, setConfirming] = useState(false);
    const [result, setResult] = useState(null);

    const sortedPeriods = useMemo(
        () => [...(periods.data ?? [])].sort((a, b) => b.code.localeCompare(a.code)),
        [periods.data],
    );

    const periodId = chosenPeriodId || (sortedPeriods[0] ? String(sortedPeriods[0].id) : '');
    const period = sortedPeriods.find((item) => String(item.id) === periodId) ?? null;

    const loadReadiness = useCallback(
        () => (periodId ? academicPeriodService.closureReadiness(periodId) : Promise.resolve(null)),
        [periodId],
    );
    const readiness = useApiData(loadReadiness);
    const diagnosis = readiness.data;

    const { call: close, error: closeError, loading: closing, reset: resetCloseError } = useApiCall(
        useCallback(() => academicClosureService.close(periodId), [periodId]),
    );

    // La tabla ordena por seccion: la clave de cada fila es el id de su seccion
    const rows = useMemo(
        () => (diagnosis?.sections ?? []).map((row) => ({ ...row, id: row.section.id })),
        [diagnosis],
    );
    const totals = useMemo(
        () => ({
            enrolled: diagnosis?.enrolledCount ?? 0,
            graded: diagnosis?.gradedCount ?? 0,
            pending: diagnosis?.pendingCount ?? 0,
        }),
        [diagnosis],
    );
    const pendingSubjects = diagnosis?.pendingSubjectCodes ?? [];

    const handlePeriodChange = (event) => {
        setChosenPeriodId(event.target.value);
        resetCloseError();
    };

    const handleClose = async () => {
        const { ok, data } = await close();
        if (!ok) return;

        setConfirming(false);
        setResult(data);
        // El periodo ya no esta abierto: la lista cambia y la seleccion vuelve al default
        setChosenPeriodId('');
        periods.reload();
    };

    return (
        <>
            <PageHeader
                title="Cierre de periodo academico"
                subtitle="Consolida el historial y define los estados terminales. No tiene retorno."
            />

            {result && <ClosureResult result={result} onDismiss={() => setResult(null)} />}

            <ErrorAlert error={periods.error} onRetry={periods.reload} />
            <ErrorAlert error={readiness.error} onRetry={readiness.reload} />
            <ErrorAlert error={closeError} onClose={resetCloseError} />

            {periods.loading ? (
                <Loader label="Cargando periodos..." />
            ) : sortedPeriods.length === 0 ? (
                <Paper variant="outlined">
                    <EmptyState
                        title="No hay periodos abiertos"
                        description="Todos los periodos estan cerrados: no hay nada que consolidar."
                    />
                </Paper>
            ) : (
                <>
                    <Box sx={{ maxWidth: 320, mb: 3 }}>
                        <SelectField
                            label="Periodo a cerrar"
                            value={periodId}
                            onChange={handlePeriodChange}
                            options={sortedPeriods.map((item) => ({ value: String(item.id), label: item.code }))}
                            helperText="Solo periodos abiertos"
                        />
                    </Box>

                    <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
                        <Stack
                            direction={{ xs: 'column', md: 'row' }}
                            spacing={2}
                            sx={{ alignItems: { md: 'center' }, justifyContent: 'space-between' }}
                        >
                            <Box>
                                <Typography variant="subtitle1">Periodo {period?.code}</Typography>
                                <Typography variant="body2" color="text.secondary">
                                    {rows.length} secciones · {totals.enrolled} inscripciones · {totals.graded} con
                                    nota · {totals.pending} pendientes
                                </Typography>
                            </Box>

                            <Button
                                variant="contained"
                                color="error"
                                size="large"
                                disabled={readiness.loading || !diagnosis?.canClose || closing}
                                onClick={() => setConfirming(true)}
                            >
                                Cerrar periodo
                            </Button>
                        </Stack>
                    </Paper>

                    {!readiness.loading && totals.pending > 0 && (
                        <Alert severity="warning" sx={{ mb: 2 }}>
                            <AlertTitle>Faltan notas para cerrar</AlertTitle>
                            Quedan {totals.pending} inscripciones sin calificar en{' '}
                            {pendingSubjects.length === 1 ? 'la asignatura' : 'las asignaturas'}{' '}
                            {pendingSubjects.join(', ')}. El docente responsable de cada seccion debe registrarlas.
                        </Alert>
                    )}

                    {!readiness.loading && rows.length > 0 && totals.pending === 0 && (
                        <Alert severity="info" sx={{ mb: 2 }}>
                            Todas las notas estan registradas: el periodo se puede cerrar.
                        </Alert>
                    )}

                    <ClosureReadiness rows={rows} loading={readiness.loading} />
                </>
            )}

            <ConfirmDialog
                open={confirming}
                title={`Cerrar el periodo ${period?.code}`}
                message={`Se copiaran ${totals.enrolled} inscripciones al historial academico y se recalcularan los estados de los estudiantes: EGRESADO si completaron su plan, ELIMINADO si reprobaron dos veces la misma asignatura. El periodo queda CERRADO y no se puede reabrir.`}
                confirmLabel="Cerrar periodo"
                loading={closing}
                error={closeError}
                onConfirm={handleClose}
                onClose={() => {
                    setConfirming(false);
                    resetCloseError();
                }}
            />
        </>
    );
}
