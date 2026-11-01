import { useCallback, useMemo, useState } from 'react';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import HowToRegIcon from '@mui/icons-material/HowToReg';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import Stack from '@mui/material/Stack';
import Tooltip from '@mui/material/Tooltip';
import StatusChip from '../../components/domain/StatusChip';
import SelectField from '../../components/form/SelectField';
import ConfirmDialog from '../../components/ui/ConfirmDialog';
import DataTable from '../../components/ui/DataTable';
import ErrorAlert from '../../components/ui/ErrorAlert';
import PageHeader from '../../components/ui/PageHeader';
import { useApiData } from '../../hooks/useApiData';
import { useApiResource } from '../../hooks/useApiResource';
import { useToast } from '../../hooks/useToast';
import { academicPeriodService } from '../../services/academicPeriodService';
import { careerService } from '../../services/careerService';
import { studentService } from '../../services/studentService';
import { MANUAL_STUDENT_STATUSES, STUDENT_STATUS, statusOptions } from '../../utils/enums';
import { formatRun } from '../../utils/formatters';
import MatriculationDialog from './MatriculationDialog';
import StudentForm from './StudentForm';

/**
 * Estudiantes. Dos reglas se ven en la tabla: el estado solo se puede mover
 * entre REGULAR, POSTERGACION y RETIRO_TEMPORAL -EGRESADO y ELIMINADO los pone
 * el cierre de periodo y son terminales-, y la matricula por periodo es
 * requisito para inscribir, por eso tiene su propia accion.
 */
export default function StudentsPage() {
    const [statusFilter, setStatusFilter] = useState('');
    const students = useApiResource(studentService, {
        params: statusFilter ? { status: statusFilter } : undefined,
    });

    const loadCareers = useCallback(() => careerService.list(), []);
    const careers = useApiData(loadCareers);
    const loadOpenPeriods = useCallback(() => academicPeriodService.list({ status: 'ABIERTO' }), []);
    const openPeriods = useApiData(loadOpenPeriods);

    const { showSuccess } = useToast();

    const [careerFilter, setCareerFilter] = useState('');
    const [editing, setEditing] = useState(null);
    const [matriculating, setMatriculating] = useState(null);
    const [deleting, setDeleting] = useState(null);
    const [deleteError, setDeleteError] = useState(null);
    const [deleteLoading, setDeleteLoading] = useState(false);

    const visibleStudents = useMemo(
        () =>
            students.items
                .filter((student) => !careerFilter || String(student.careerId) === careerFilter)
                .sort((a, b) => a.fullName.localeCompare(b.fullName)),
        [students.items, careerFilter],
    );

    const handleStatusChange = async (row, next) => {
        const { ok } = await students.mutate(() => studentService.changeStatus(row.id, next));
        if (ok) showSuccess(`${row.fullName} quedo ${next}`);
    };

    const columns = [
        { field: 'run', label: 'RUN', width: 140, render: (row) => formatRun(row.run) },
        { field: 'fullName', label: 'Nombre' },
        { field: 'email', label: 'Email', width: 210 },
        { field: 'careerCode', label: 'Carrera', width: 90 },
        { field: 'studyPlanIdentifier', label: 'Plan', width: 110 },
        {
            field: 'status',
            label: 'Estado',
            width: 200,
            render: (row) =>
                MANUAL_STUDENT_STATUSES.includes(row.status) ? (
                    <SelectField
                        value={row.status}
                        onChange={(event) => handleStatusChange(row, event.target.value)}
                        options={statusOptions(STUDENT_STATUS, MANUAL_STUDENT_STATUSES)}
                        margin="none"
                    />
                ) : (
                    <StatusChip kind="student" value={row.status} />
                ),
        },
    ];

    const handleSubmit = async (values) => {
        const result = editing?.id ? await students.update(editing.id, values) : await students.create(values);

        if (!result.ok) {
            students.setError(null);
            throw result.error;
        }
        showSuccess(editing?.id ? 'Estudiante actualizado' : 'Estudiante creado');
        return result.data;
    };

    const handleDelete = async () => {
        setDeleteLoading(true);
        setDeleteError(null);
        const { ok, error } = await students.remove(deleting.id);
        setDeleteLoading(false);

        if (ok) {
            setDeleting(null);
            showSuccess('Estudiante eliminado');
        } else {
            setDeleteError(error);
        }
    };

    return (
        <>
            <PageHeader
                title="Estudiantes"
                subtitle="El plan lo asigna el sistema (el VIGENTE de la carrera). EGRESADO y ELIMINADO los define el cierre de periodo."
                action={
                    <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEditing({})}>
                        Nuevo estudiante
                    </Button>
                }
            />

            <ErrorAlert error={students.error} onRetry={students.reload} onClose={() => students.setError(null)} />
            <ErrorAlert error={careers.error} onRetry={careers.reload} />

            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2 }}>
                <Box sx={{ minWidth: 220 }}>
                    <SelectField
                        label="Estado"
                        value={statusFilter}
                        onChange={(event) => setStatusFilter(event.target.value)}
                        options={statusOptions(STUDENT_STATUS)}
                        placeholder="Todos"
                    />
                </Box>
                <Box sx={{ minWidth: 300 }}>
                    <SelectField
                        label="Carrera"
                        value={careerFilter}
                        onChange={(event) => setCareerFilter(event.target.value)}
                        options={(careers.data ?? []).map((career) => ({
                            value: String(career.id),
                            label: `${career.code} — ${career.name}`,
                        }))}
                        placeholder="Todas"
                    />
                </Box>
            </Stack>

            <DataTable
                columns={columns}
                rows={visibleStudents}
                loading={students.loading}
                emptyTitle="No hay estudiantes"
                actions={(row) => (
                    <Stack direction="row" spacing={0.5} sx={{ justifyContent: 'flex-end' }}>
                        <Tooltip title="Matricula por periodo">
                            <IconButton size="small" onClick={() => setMatriculating(row)}>
                                <HowToRegIcon fontSize="small" />
                            </IconButton>
                        </Tooltip>
                        <Tooltip title="Editar">
                            <IconButton size="small" onClick={() => setEditing(row)}>
                                <EditIcon fontSize="small" />
                            </IconButton>
                        </Tooltip>
                        <Tooltip title="Eliminar">
                            <IconButton size="small" onClick={() => setDeleting(row)}>
                                <DeleteIcon fontSize="small" />
                            </IconButton>
                        </Tooltip>
                    </Stack>
                )}
            />

            <StudentForm
                open={Boolean(editing)}
                student={editing?.id ? editing : null}
                careers={careers.data ?? []}
                onSubmit={handleSubmit}
                onClose={() => setEditing(null)}
            />

            <MatriculationDialog
                open={Boolean(matriculating)}
                student={matriculating}
                openPeriods={openPeriods.data ?? []}
                onClose={() => setMatriculating(null)}
            />

            <ConfirmDialog
                open={Boolean(deleting)}
                title="Eliminar estudiante"
                message={`Se eliminara a ${deleting?.fullName}. No se puede eliminar si tiene inscripciones o historial academico; sus matriculas si se borran con el.`}
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
