import { useCallback, useMemo, useState } from 'react';
import AddIcon from '@mui/icons-material/Add';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
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
import { careerService } from '../../services/careerService';
import { studyPlanService } from '../../services/studyPlanService';
import StudyPlanForm from './StudyPlanForm';

/**
 * Planes de estudio. La regla visible de esta pantalla: a lo mas un plan
 * VIGENTE por carrera, y dejar uno vigente degrada al anterior, por eso cada
 * cambio recarga la lista completa.
 */
export default function StudyPlansPage() {
    const plans = useApiResource(studyPlanService);
    const loadCareers = useCallback(() => careerService.list(), []);
    const careers = useApiData(loadCareers);
    const { showSuccess } = useToast();

    const [careerFilter, setCareerFilter] = useState('');
    const [editing, setEditing] = useState(null);
    const [deleting, setDeleting] = useState(null);
    const [deleteError, setDeleteError] = useState(null);
    const [deleteLoading, setDeleteLoading] = useState(false);
    const [promoting, setPromoting] = useState(null);
    const [promoteError, setPromoteError] = useState(null);
    const [promoteLoading, setPromoteLoading] = useState(false);

    const visiblePlans = useMemo(
        () =>
            plans.items
                .filter((plan) => !careerFilter || String(plan.careerId) === careerFilter)
                .sort(
                    (a, b) =>
                        (a.careerName ?? '').localeCompare(b.careerName ?? '') ||
                        a.identifier.localeCompare(b.identifier),
                ),
        [plans.items, careerFilter],
    );

    const columns = [
        { field: 'identifier', label: 'Identificador', width: 150 },
        { field: 'name', label: 'Nombre' },
        { field: 'careerName', label: 'Carrera', width: 220 },
        {
            field: 'status',
            label: 'Estado',
            width: 130,
            render: (row) => <StatusChip kind="studyPlan" value={row.status} />,
        },
    ];

    const handleSubmit = async (values) => {
        const result = editing?.id ? await plans.update(editing.id, values) : await plans.create(values);

        if (!result.ok) {
            plans.setError(null);
            throw result.error;
        }
        showSuccess(editing?.id ? 'Plan actualizado' : 'Plan creado');
        return result.data;
    };

    const handlePromote = async () => {
        setPromoteLoading(true);
        setPromoteError(null);
        const { ok, error } = await plans.mutate(() => studyPlanService.changeStatus(promoting.id, 'VIGENTE'));
        setPromoteLoading(false);

        if (ok) {
            setPromoting(null);
            showSuccess(`${promoting.identifier} quedo VIGENTE`);
        } else {
            setPromoteError(error);
        }
    };

    const handleDelete = async () => {
        setDeleteLoading(true);
        setDeleteError(null);
        const { ok, error } = await plans.remove(deleting.id);
        setDeleteLoading(false);

        if (ok) {
            setDeleting(null);
            showSuccess('Plan eliminado');
        } else {
            setDeleteError(error);
        }
    };

    return (
        <>
            <PageHeader
                title="Planes de estudio"
                subtitle="El identificador es unico por carrera y solo un plan puede estar VIGENTE."
                action={
                    <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEditing({})}>
                        Nuevo plan
                    </Button>
                }
            />

            <ErrorAlert error={plans.error} onRetry={plans.reload} onClose={() => plans.setError(null)} />
            <ErrorAlert error={careers.error} onRetry={careers.reload} />

            <Box sx={{ maxWidth: 320, mb: 2 }}>
                <SelectField
                    label="Carrera"
                    value={careerFilter}
                    onChange={(event) => setCareerFilter(event.target.value)}
                    options={(careers.data ?? []).map((career) => ({
                        value: String(career.id),
                        label: `${career.code} — ${career.name}`,
                    }))}
                    placeholder="Todas las carreras"
                />
            </Box>

            <DataTable
                columns={columns}
                rows={visiblePlans}
                loading={plans.loading}
                emptyTitle="No hay planes de estudio"
                actions={(row) => (
                    <Stack direction="row" spacing={0.5} sx={{ justifyContent: 'flex-end' }}>
                        {row.status !== 'VIGENTE' && (
                            <Tooltip title="Dejar VIGENTE">
                                <IconButton size="small" onClick={() => setPromoting(row)}>
                                    <CheckCircleIcon fontSize="small" />
                                </IconButton>
                            </Tooltip>
                        )}
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

            <StudyPlanForm
                open={Boolean(editing)}
                plan={editing?.id ? editing : null}
                careers={careers.data ?? []}
                onSubmit={handleSubmit}
                onClose={() => setEditing(null)}
            />

            <ConfirmDialog
                open={Boolean(promoting)}
                title="Dejar el plan VIGENTE"
                message={`${promoting?.identifier} pasara a VIGENTE y el plan vigente actual de ${promoting?.careerName} quedara NO_VIGENTE. Los estudiantes ya registrados mantienen su plan.`}
                confirmLabel="Dejar vigente"
                confirmColor="primary"
                loading={promoteLoading}
                error={promoteError}
                onConfirm={handlePromote}
                onClose={() => {
                    setPromoting(null);
                    setPromoteError(null);
                }}
            />

            <ConfirmDialog
                open={Boolean(deleting)}
                title="Eliminar plan de estudios"
                message={`Se eliminara ${deleting?.identifier}. No se puede eliminar si tiene estudiantes asociados.`}
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
