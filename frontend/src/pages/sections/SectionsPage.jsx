import { useCallback, useMemo, useState } from 'react';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import Stack from '@mui/material/Stack';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import ScheduleBlocks from '../../components/domain/ScheduleBlocks';
import SelectField from '../../components/form/SelectField';
import ConfirmDialog from '../../components/ui/ConfirmDialog';
import DataTable from '../../components/ui/DataTable';
import ErrorAlert from '../../components/ui/ErrorAlert';
import PageHeader from '../../components/ui/PageHeader';
import { useApiData } from '../../hooks/useApiData';
import { useApiResource } from '../../hooks/useApiResource';
import { useToast } from '../../hooks/useToast';
import { academicPeriodService } from '../../services/academicPeriodService';
import { sectionService } from '../../services/sectionService';
import { subjectService } from '../../services/subjectService';
import { teacherService } from '../../services/teacherService';
import SectionForm from './SectionForm';

/**
 * Oferta academica. Las secciones solo se crean y modifican en periodos
 * ABIERTO, por eso el formulario ofrece unicamente esos; el filtro de la tabla
 * en cambio muestra cualquier periodo, para poder consultar la oferta historica.
 */
export default function SectionsPage() {
    const [periodFilter, setPeriodFilter] = useState('');
    const sections = useApiResource(sectionService, {
        params: periodFilter ? { academicPeriodId: Number(periodFilter) } : undefined,
    });

    const loadPeriods = useCallback(() => academicPeriodService.list(), []);
    const periods = useApiData(loadPeriods);
    const loadSubjects = useCallback(() => subjectService.list(), []);
    const subjects = useApiData(loadSubjects);
    const loadTeachers = useCallback(() => teacherService.list(), []);
    const teachers = useApiData(loadTeachers);

    const { showSuccess } = useToast();

    const [editing, setEditing] = useState(null);
    const [deleting, setDeleting] = useState(null);
    const [deleteError, setDeleteError] = useState(null);
    const [deleteLoading, setDeleteLoading] = useState(false);

    const openPeriods = useMemo(
        () => (periods.data ?? []).filter((period) => period.status === 'ABIERTO'),
        [periods.data],
    );
    const closedPeriodIds = useMemo(
        () => new Set((periods.data ?? []).filter((period) => period.status !== 'ABIERTO').map((p) => p.id)),
        [periods.data],
    );

    /** Para asignar hace falta un docente ACTIVO; el ya asignado se mantiene. */
    const assignableTeachers = useMemo(() => {
        const list = (teachers.data ?? []).filter((teacher) => teacher.status === 'ACTIVO');
        const current = (teachers.data ?? []).find((teacher) => teacher.id === editing?.teacherId);
        return current && !list.includes(current) ? [current, ...list] : list;
    }, [teachers.data, editing]);

    const visibleSections = useMemo(
        () =>
            [...sections.items].sort(
                (a, b) =>
                    b.academicPeriodCode.localeCompare(a.academicPeriodCode) ||
                    a.subjectCode.localeCompare(b.subjectCode),
            ),
        [sections.items],
    );

    const columns = [
        {
            field: 'subjectCode',
            label: 'Asignatura',
            render: (row) => (
                <>
                    <Typography variant="body2">{row.subjectCode}</Typography>
                    <Typography variant="caption" color="text.secondary">
                        {row.subjectName}
                    </Typography>
                </>
            ),
        },
        { field: 'academicPeriodCode', label: 'Periodo', width: 110 },
        { field: 'teacherFullName', label: 'Docente', width: 200 },
        { field: 'capacity', label: 'Cupos', align: 'center', width: 90 },
        {
            field: 'scheduleBlocks',
            label: 'Horario',
            width: 190,
            render: (row) => <ScheduleBlocks blocks={row.scheduleBlocks} />,
        },
    ];

    const handleSubmit = async (values) => {
        const result = editing?.id ? await sections.update(editing.id, values) : await sections.create(values);

        if (!result.ok) {
            sections.setError(null);
            throw result.error;
        }
        showSuccess(editing?.id ? 'Seccion actualizada' : 'Seccion creada');
        return result.data;
    };

    const handleDelete = async () => {
        setDeleteLoading(true);
        setDeleteError(null);
        const { ok, error } = await sections.remove(deleting.id);
        setDeleteLoading(false);

        if (ok) {
            setDeleting(null);
            showSuccess('Seccion eliminada');
        } else {
            setDeleteError(error);
        }
    };

    return (
        <>
            <PageHeader
                title="Secciones"
                subtitle="Una seccion por asignatura y periodo, con docente ACTIVO y horario sin choques."
                action={
                    <Button
                        variant="contained"
                        startIcon={<AddIcon />}
                        onClick={() => setEditing({})}
                        disabled={openPeriods.length === 0}
                    >
                        Nueva seccion
                    </Button>
                }
            />

            <ErrorAlert error={sections.error} onRetry={sections.reload} onClose={() => sections.setError(null)} />
            <ErrorAlert error={periods.error} onRetry={periods.reload} />

            <Box sx={{ maxWidth: 320, mb: 2 }}>
                <SelectField
                    label="Periodo academico"
                    value={periodFilter}
                    onChange={(event) => setPeriodFilter(event.target.value)}
                    options={[...(periods.data ?? [])]
                        .sort((a, b) => b.code.localeCompare(a.code))
                        .map((period) => ({
                            value: String(period.id),
                            label: `${period.code}${period.status === 'ABIERTO' ? '' : ' (cerrado)'}`,
                        }))}
                    placeholder="Todos los periodos"
                />
            </Box>

            <DataTable
                columns={columns}
                rows={visibleSections}
                loading={sections.loading}
                emptyTitle="No hay secciones"
                emptyDescription="Crea la oferta academica de un periodo abierto."
                actions={(row) => {
                    const frozen = closedPeriodIds.has(row.academicPeriodId);
                    return (
                        <Stack direction="row" spacing={0.5} sx={{ justifyContent: 'flex-end' }}>
                            <Tooltip title={frozen ? 'El periodo esta cerrado' : 'Editar'}>
                                <span>
                                    <IconButton size="small" onClick={() => setEditing(row)} disabled={frozen}>
                                        <EditIcon fontSize="small" />
                                    </IconButton>
                                </span>
                            </Tooltip>
                            <Tooltip title={frozen ? 'El periodo esta cerrado' : 'Eliminar'}>
                                <span>
                                    <IconButton size="small" onClick={() => setDeleting(row)} disabled={frozen}>
                                        <DeleteIcon fontSize="small" />
                                    </IconButton>
                                </span>
                            </Tooltip>
                        </Stack>
                    );
                }}
            />

            <SectionForm
                open={Boolean(editing)}
                section={editing?.id ? editing : null}
                subjects={subjects.data ?? []}
                periods={openPeriods}
                teachers={assignableTeachers}
                onSubmit={handleSubmit}
                onClose={() => setEditing(null)}
            />

            <ConfirmDialog
                open={Boolean(deleting)}
                title="Eliminar seccion"
                message={`Se eliminara la seccion de ${deleting?.subjectCode} en ${deleting?.academicPeriodCode}. No se puede eliminar si tiene inscripciones, incluso anuladas.`}
                confirmLabel="Eliminar"
                loading={deleteLoading}
                error={deleteError}
                onConfirm={handleDelete}
                onClose={() => {
                    setDeleting(null);
                    setDeleteError(null);
                }}
            />
        </>
    );
}
