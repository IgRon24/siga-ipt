import { useState } from 'react';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import ToggleOffIcon from '@mui/icons-material/ToggleOff';
import ToggleOnIcon from '@mui/icons-material/ToggleOn';
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
import { useApiResource } from '../../hooks/useApiResource';
import { useToast } from '../../hooks/useToast';
import { teacherService } from '../../services/teacherService';
import { TEACHER_STATUS, statusOptions } from '../../utils/enums';
import { formatRun } from '../../utils/formatters';
import TeacherForm from './TeacherForm';

export default function TeachersPage() {
    const [statusFilter, setStatusFilter] = useState('');
    const teachers = useApiResource(teacherService, {
        params: statusFilter ? { status: statusFilter } : undefined,
    });
    const { showSuccess } = useToast();

    const [editing, setEditing] = useState(null);
    const [deleting, setDeleting] = useState(null);
    const [deleteError, setDeleteError] = useState(null);
    const [deleteLoading, setDeleteLoading] = useState(false);

    const columns = [
        { field: 'run', label: 'RUN', width: 140, render: (row) => formatRun(row.run) },
        { field: 'fullName', label: 'Nombre' },
        { field: 'email', label: 'Email', width: 220 },
        { field: 'academicDegree', label: 'Grado', width: 200 },
        {
            field: 'status',
            label: 'Estado',
            width: 120,
            render: (row) => <StatusChip kind="teacher" value={row.status} />,
        },
    ];

    const handleSubmit = async (values) => {
        const result = editing?.id
            ? await teachers.update(editing.id, values)
            : await teachers.create(values);

        if (!result.ok) {
            teachers.setError(null);
            throw result.error;
        }
        showSuccess(editing?.id ? 'Docente actualizado' : 'Docente creado');
        return result.data;
    };

    const handleToggleStatus = async (row) => {
        const next = row.status === 'ACTIVO' ? 'INACTIVO' : 'ACTIVO';
        const { ok } = await teachers.mutate(() => teacherService.changeStatus(row.id, next));
        if (ok) showSuccess(`${row.fullName} quedo ${next}`);
    };

    const handleDelete = async () => {
        setDeleteLoading(true);
        setDeleteError(null);
        const { ok, error } = await teachers.remove(deleting.id);
        setDeleteLoading(false);

        if (ok) {
            setDeleting(null);
            showSuccess('Docente eliminado');
        } else {
            setDeleteError(error);
        }
    };

    return (
        <>
            <PageHeader
                title="Docentes"
                subtitle="Solo un docente ACTIVO puede recibir una seccion; el estado se exige al asignarla."
                action={
                    <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEditing({})}>
                        Nuevo docente
                    </Button>
                }
            />

            <ErrorAlert error={teachers.error} onRetry={teachers.reload} onClose={() => teachers.setError(null)} />

            <Box sx={{ maxWidth: 260, mb: 2 }}>
                <SelectField
                    label="Estado"
                    value={statusFilter}
                    onChange={(event) => setStatusFilter(event.target.value)}
                    options={statusOptions(TEACHER_STATUS)}
                    placeholder="Todos"
                />
            </Box>

            <DataTable
                columns={columns}
                rows={teachers.items}
                loading={teachers.loading}
                emptyTitle="No hay docentes registrados"
                actions={(row) => (
                    <Stack direction="row" spacing={0.5} sx={{ justifyContent: 'flex-end' }}>
                        <Tooltip title={row.status === 'ACTIVO' ? 'Dejar INACTIVO' : 'Dejar ACTIVO'}>
                            <IconButton size="small" onClick={() => handleToggleStatus(row)}>
                                {row.status === 'ACTIVO' ? (
                                    <ToggleOnIcon fontSize="small" color="success" />
                                ) : (
                                    <ToggleOffIcon fontSize="small" />
                                )}
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

            <TeacherForm
                open={Boolean(editing)}
                teacher={editing?.id ? editing : null}
                onSubmit={handleSubmit}
                onClose={() => setEditing(null)}
            />

            <ConfirmDialog
                open={Boolean(deleting)}
                title="Eliminar docente"
                message={`Se eliminara a ${deleting?.fullName}. No se puede eliminar si tiene secciones asignadas.`}
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
