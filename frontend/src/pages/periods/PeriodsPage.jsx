import { useState } from 'react';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import Stack from '@mui/material/Stack';
import Tooltip from '@mui/material/Tooltip';
import StatusChip from '../../components/domain/StatusChip';
import ConfirmDialog from '../../components/ui/ConfirmDialog';
import DataTable from '../../components/ui/DataTable';
import ErrorAlert from '../../components/ui/ErrorAlert';
import PageHeader from '../../components/ui/PageHeader';
import { useApiResource } from '../../hooks/useApiResource';
import { useToast } from '../../hooks/useToast';
import { academicPeriodService } from '../../services/academicPeriodService';
import { formatPeriod } from '../../utils/formatters';
import PeriodForm from './PeriodForm';

/**
 * Periodos academicos. El cierre no vive aqui: pasar a CERRADO consolida el
 * historial y tiene su propia pantalla, "Cierre de periodo".
 */
export default function PeriodsPage() {
    const periods = useApiResource(academicPeriodService);
    const { showSuccess } = useToast();

    const [editing, setEditing] = useState(null);
    const [deleting, setDeleting] = useState(null);
    const [deleteError, setDeleteError] = useState(null);
    const [deleteLoading, setDeleteLoading] = useState(false);

    const columns = [
        { field: 'code', label: 'Codigo', width: 110 },
        { field: 'period', label: 'Periodo', render: (row) => formatPeriod(row) },
        {
            field: 'status',
            label: 'Estado',
            width: 130,
            render: (row) => <StatusChip kind="period" value={row.status} />,
        },
    ];

    const handleSubmit = async (values) => {
        const result = editing?.id
            ? await periods.update(editing.id, values)
            : await periods.create(values);

        if (!result.ok) {
            periods.setError(null);
            throw result.error;
        }
        showSuccess(editing?.id ? 'Periodo actualizado' : 'Periodo creado');
        return result.data;
    };

    const handleDelete = async () => {
        setDeleteLoading(true);
        setDeleteError(null);
        const { ok, error } = await periods.remove(deleting.id);
        setDeleteLoading(false);

        if (ok) {
            setDeleting(null);
            showSuccess('Periodo eliminado');
        } else {
            setDeleteError(error);
        }
    };

    return (
        <>
            <PageHeader
                title="Periodos academicos"
                subtitle="Un periodo por anio y semestre. Nacen ABIERTO y el paso a CERRADO no tiene retorno."
                action={
                    <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEditing({})}>
                        Nuevo periodo
                    </Button>
                }
            />

            <ErrorAlert error={periods.error} onRetry={periods.reload} onClose={() => periods.setError(null)} />

            <Alert severity="info" sx={{ mb: 2 }}>
                Para cerrar un periodo usa la pantalla <strong>Cierre de periodo</strong>: ahi se revisa que no falten
                notas y se consolida el historial academico.
            </Alert>

            <DataTable
                columns={columns}
                rows={periods.items}
                loading={periods.loading}
                emptyTitle="No hay periodos registrados"
                actions={(row) => (
                    <Stack direction="row" spacing={0.5} sx={{ justifyContent: 'flex-end' }}>
                        <Tooltip title={row.status === 'ABIERTO' ? 'Editar' : 'Un periodo CERRADO no se modifica'}>
                            <span>
                                <IconButton
                                    size="small"
                                    onClick={() => setEditing(row)}
                                    disabled={row.status !== 'ABIERTO'}
                                >
                                    <EditIcon fontSize="small" />
                                </IconButton>
                            </span>
                        </Tooltip>
                        <Tooltip title={row.status === 'ABIERTO' ? 'Eliminar' : 'Un periodo CERRADO es historico'}>
                            <span>
                                <IconButton
                                    size="small"
                                    onClick={() => setDeleting(row)}
                                    disabled={row.status !== 'ABIERTO'}
                                >
                                    <DeleteIcon fontSize="small" />
                                </IconButton>
                            </span>
                        </Tooltip>
                    </Stack>
                )}
            />

            <PeriodForm
                open={Boolean(editing)}
                period={editing?.id ? editing : null}
                onSubmit={handleSubmit}
                onClose={() => setEditing(null)}
            />

            <ConfirmDialog
                open={Boolean(deleting)}
                title="Eliminar periodo"
                message={`Se eliminara el periodo ${deleting?.code}. No se puede eliminar si tiene secciones o matriculas registradas.`}
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
