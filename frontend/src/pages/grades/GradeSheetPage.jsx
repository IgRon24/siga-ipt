import { useCallback, useMemo, useState } from 'react';
import SaveIcon from '@mui/icons-material/Save';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import IconButton from '@mui/material/IconButton';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import ScheduleBlocks from '../../components/domain/ScheduleBlocks';
import StatusChip from '../../components/domain/StatusChip';
import SelectField from '../../components/form/SelectField';
import TextField from '../../components/form/TextField';
import DataTable from '../../components/ui/DataTable';
import EmptyState from '../../components/ui/EmptyState';
import ErrorAlert from '../../components/ui/ErrorAlert';
import Loader from '../../components/ui/Loader';
import PageHeader from '../../components/ui/PageHeader';
import { useApiCall } from '../../hooks/useApiCall';
import { useApiData } from '../../hooks/useApiData';
import { useTeacherSections } from '../../hooks/useTeacherSections';
import { useToast } from '../../hooks/useToast';
import { gradeService } from '../../services/gradeService';
import { formatRun } from '../../utils/formatters';
import { isValidationError } from '../../utils/httpError';
import { required, validGrade } from '../../utils/validators';

/**
 * Acta de una seccion: la unica pantalla que escribe notas.
 *
 * Reglas del backend que se reflejan aqui: solo el docente responsable
 * califica (el backend lo resuelve por el email del token, no se envia), solo
 * mientras el periodo esta ABIERTO, una unica nota final por inscripcion -de
 * ahi que guardar sea POST la primera vez y PUT despues- y el resultado
 * APROBADO/REPROBADO lo calcula el servidor, nunca el cliente.
 *
 * Se guarda fila por fila porque no hay endpoint por lote: cada nota es una
 * llamada y su error pertenece a ese estudiante.
 */
export default function GradeSheetPage() {
    const { sections, teacherMissing, periodStatusById, loading, error, reload } = useTeacherSections();
    const { showSuccess } = useToast();

    const [chosenSectionId, setChosenSectionId] = useState('');
    const [drafts, setDrafts] = useState({});
    const [rowErrors, setRowErrors] = useState({});
    const [savingEnrollmentId, setSavingEnrollmentId] = useState(null);

    // Las secciones de periodos abiertos primero: son las que se pueden calificar
    const sortedSections = useMemo(
        () =>
            [...sections].sort((a, b) => {
                const openA = periodStatusById.get(a.academicPeriodId) === 'ABIERTO' ? 0 : 1;
                const openB = periodStatusById.get(b.academicPeriodId) === 'ABIERTO' ? 0 : 1;
                return (
                    openA - openB ||
                    b.academicPeriodCode.localeCompare(a.academicPeriodCode) ||
                    a.subjectCode.localeCompare(b.subjectCode)
                );
            }),
        [sections, periodStatusById],
    );

    // La seleccion por defecto se deriva, no se guarda en un efecto
    const sectionId = chosenSectionId || (sortedSections[0] ? String(sortedSections[0].id) : '');
    const section = sortedSections.find((item) => String(item.id) === sectionId) ?? null;
    const periodOpen = section ? periodStatusById.get(section.academicPeriodId) === 'ABIERTO' : false;

    const loadSheet = useCallback(
        () => (sectionId ? gradeService.sheet(sectionId) : Promise.resolve([])),
        [sectionId],
    );
    const sheet = useApiData(loadSheet);

    const saveGrade = useCallback(
        (row, value) =>
            row.id === null || row.id === undefined
                ? gradeService.create(row.enrollmentId, value)
                : gradeService.update(row.id, row.enrollmentId, value),
        [],
    );
    const { call: submitGrade, error: saveError, reset: resetSaveError } = useApiCall(saveGrade);

    const rows = sheet.data ?? [];
    const graded = rows.filter((row) => row.value !== null && row.value !== undefined).length;

    const sectionOptions = sortedSections.map((item) => ({
        value: String(item.id),
        label:
            `${item.academicPeriodCode} · ${item.subjectCode} — ${item.subjectName}` +
            (periodStatusById.get(item.academicPeriodId) === 'ABIERTO' ? '' : ' (periodo cerrado)'),
    }));

    const handleSectionChange = (event) => {
        setChosenSectionId(event.target.value);
        setDrafts({});
        setRowErrors({});
        resetSaveError();
    };

    const draftOf = (row) => drafts[row.enrollmentId] ?? (row.value === null ? '' : String(row.value));

    const isDirty = (row) => {
        const draft = drafts[row.enrollmentId];
        if (draft === undefined) return false;
        const current = row.value === null || row.value === undefined ? '' : String(row.value);
        return draft.trim() !== current;
    };

    const handleDraftChange = (row, value) => {
        setDrafts((current) => ({ ...current, [row.enrollmentId]: value }));
        setRowErrors((current) => (current[row.enrollmentId] ? { ...current, [row.enrollmentId]: undefined } : current));
    };

    const handleSave = async (row) => {
        const value = draftOf(row).trim().replace(',', '.');
        const localError = required(value, 'Ingresa la nota') ?? validGrade(value);
        if (localError) {
            setRowErrors((current) => ({ ...current, [row.enrollmentId]: localError }));
            return;
        }

        setSavingEnrollmentId(row.enrollmentId);
        const { ok, error: apiError } = await submitGrade(row, Number(value));
        setSavingEnrollmentId(null);

        if (ok) {
            setDrafts((current) => {
                const next = { ...current };
                delete next[row.enrollmentId];
                return next;
            });
            showSuccess(`Nota de ${row.studentFullName} registrada`);
            sheet.reload();
            return;
        }

        // Un 400 del backend trae el error en el campo value: va a esa fila
        if (isValidationError(apiError)) {
            setRowErrors((current) => ({
                ...current,
                [row.enrollmentId]: apiError.fieldErrors.value ?? apiError.message,
            }));
        }
    };

    const columns = [
        { field: 'studentRun', label: 'RUN', width: 140, render: (row) => formatRun(row.studentRun) },
        { field: 'studentFullName', label: 'Estudiante' },
        {
            field: 'value',
            label: 'Nota',
            width: 140,
            render: (row) => (
                <TextField
                    value={draftOf(row)}
                    onChange={(event) => handleDraftChange(row, event.target.value)}
                    onKeyDown={(event) => {
                        if (event.key === 'Enter' && isDirty(row)) handleSave(row);
                    }}
                    error={rowErrors[row.enrollmentId]}
                    disabled={!periodOpen || savingEnrollmentId === row.enrollmentId}
                    inputProps={{ inputMode: 'decimal', style: { textAlign: 'right' } }}
                    placeholder="-"
                    margin="none"
                    sx={{ maxWidth: 110 }}
                />
            ),
        },
        {
            field: 'result',
            label: 'Resultado',
            width: 150,
            render: (row) =>
                row.result ? (
                    <StatusChip kind="result" value={row.result} />
                ) : (
                    <Typography variant="caption" color="text.secondary">
                        Sin calificar
                    </Typography>
                ),
        },
    ];

    if (teacherMissing) {
        return (
            <>
                <PageHeader title="Registro de calificaciones" />
                <ErrorAlert error={error} onRetry={reload} />
                <Paper variant="outlined">
                    <EmptyState
                        title="Tu usuario no esta registrado como docente"
                        description="Solo el docente responsable de una seccion puede registrar sus notas."
                    />
                </Paper>
            </>
        );
    }

    return (
        <>
            <PageHeader
                title="Registro de calificaciones"
                subtitle="Una nota final por inscripcion, en escala 1.0 a 7.0. Aprueba con 4.0 o mas."
                action={section && <StatusChip kind="period" value={periodStatusById.get(section.academicPeriodId)} />}
            />

            <ErrorAlert error={error} onRetry={reload} />
            <ErrorAlert error={saveError} onClose={resetSaveError} />

            <Box sx={{ maxWidth: 520, mb: 2 }}>
                <SelectField
                    label="Seccion"
                    value={sectionId}
                    onChange={handleSectionChange}
                    options={sectionOptions}
                    helperText="Primero las secciones de periodos abiertos"
                />
            </Box>

            {loading ? (
                <Loader label="Cargando tus secciones..." />
            ) : !section ? (
                <Paper variant="outlined">
                    <EmptyState
                        title="Sin secciones"
                        description="No tienes secciones asignadas, asi que no hay actas que registrar."
                    />
                </Paper>
            ) : (
                <>
                    <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
                        <Stack
                            direction={{ xs: 'column', md: 'row' }}
                            spacing={2}
                            sx={{ alignItems: { md: 'center' }, justifyContent: 'space-between' }}
                        >
                            <Box>
                                <Typography variant="subtitle1">
                                    {section.subjectCode} — {section.subjectName}
                                </Typography>
                                <Typography variant="body2" color="text.secondary">
                                    Periodo {section.academicPeriodCode} · {rows.length} inscritos de{' '}
                                    {section.capacity} cupos · {graded} con nota
                                </Typography>
                            </Box>
                            <ScheduleBlocks blocks={section.scheduleBlocks} />
                        </Stack>
                    </Paper>

                    {!periodOpen && (
                        <Alert severity="info" sx={{ mb: 2 }}>
                            El periodo {section.academicPeriodCode} esta CERRADO: el acta queda como historico y las
                            notas ya no se modifican.
                        </Alert>
                    )}

                    {periodOpen && rows.length > 0 && graded < rows.length && (
                        <Alert severity="warning" sx={{ mb: 2 }}>
                            Faltan {rows.length - graded} notas en esta seccion. Mientras falte alguna, el periodo no se
                            puede cerrar.
                        </Alert>
                    )}

                    {periodOpen && rows.length > 0 && graded === rows.length && (
                        <Alert severity="success" sx={{ mb: 2 }}>
                            Acta completa: esta seccion ya no bloquea el cierre del periodo.
                        </Alert>
                    )}

                    <DataTable
                        columns={columns}
                        rows={rows}
                        keyField="enrollmentId"
                        loading={sheet.loading}
                        emptyTitle="Sin estudiantes inscritos"
                        emptyDescription="Nadie ha inscrito esta seccion, no hay notas que registrar."
                        actions={(row) => (
                            <Tooltip title={periodOpen ? 'Guardar nota' : 'El periodo esta cerrado'}>
                                <span>
                                    <IconButton
                                        size="small"
                                        color="primary"
                                        onClick={() => handleSave(row)}
                                        disabled={!periodOpen || !isDirty(row) || savingEnrollmentId === row.enrollmentId}
                                    >
                                        <SaveIcon fontSize="small" />
                                    </IconButton>
                                </span>
                            </Tooltip>
                        )}
                    />
                </>
            )}
        </>
    );
}
