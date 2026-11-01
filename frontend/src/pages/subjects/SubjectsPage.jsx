import { useCallback, useMemo, useState } from 'react';
import AccountTreeIcon from '@mui/icons-material/AccountTree';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import Stack from '@mui/material/Stack';
import Tooltip from '@mui/material/Tooltip';
import SelectField from '../../components/form/SelectField';
import ConfirmDialog from '../../components/ui/ConfirmDialog';
import DataTable from '../../components/ui/DataTable';
import ErrorAlert from '../../components/ui/ErrorAlert';
import PageHeader from '../../components/ui/PageHeader';
import { useApiData } from '../../hooks/useApiData';
import { useApiResource } from '../../hooks/useApiResource';
import { useToast } from '../../hooks/useToast';
import { studyPlanService } from '../../services/studyPlanService';
import { subjectService } from '../../services/subjectService';
import { formatTel } from '../../utils/formatters';
import { requiredBlocks } from '../../utils/schedule';
import PrerequisitesDialog from './PrerequisitesDialog';
import SubjectForm from './SubjectForm';

export default function SubjectsPage() {
    const subjects = useApiResource(subjectService);
    const loadPlans = useCallback(() => studyPlanService.list(), []);
    const plans = useApiData(loadPlans);
    const { showSuccess } = useToast();

    const [planFilter, setPlanFilter] = useState('');
    const [editing, setEditing] = useState(null);
    const [prerequisitesOf, setPrerequisitesOf] = useState(null);
    const [deleting, setDeleting] = useState(null);
    const [deleteError, setDeleteError] = useState(null);
    const [deleteLoading, setDeleteLoading] = useState(false);

    const visibleSubjects = useMemo(
        () =>
            subjects.items
                .filter((subject) => !planFilter || String(subject.studyPlanId) === planFilter)
                .sort((a, b) => a.semester - b.semester || a.code.localeCompare(b.code)),
        [subjects.items, planFilter],
    );

    /** Las candidatas a prerrequisito son del mismo plan que la asignatura. */
    const planSubjectsOf = (subject) =>
        subjects.items.filter((candidate) => candidate.studyPlanId === subject?.studyPlanId);

    const columns = [
        { field: 'code', label: 'Codigo', width: 140 },
        { field: 'name', label: 'Asignatura' },
        { field: 'semester', label: 'Sem.', align: 'center', width: 70 },
        {
            field: 'tel',
            label: 'TEL',
            align: 'center',
            width: 110,
            render: (row) => `${formatTel(row)} (${requiredBlocks(row)} bloques)`,
        },
        { field: 'sct', label: 'SCT', align: 'center', width: 70 },
        { field: 'studyPlanName', label: 'Plan', width: 200 },
    ];

    const handleSubmit = async (values) => {
        const result = editing?.id ? await subjects.update(editing.id, values) : await subjects.create(values);

        if (!result.ok) {
            subjects.setError(null);
            throw result.error;
        }
        showSuccess(editing?.id ? 'Asignatura actualizada' : 'Asignatura creada');
        return result.data;
    };

    const handleDelete = async () => {
        setDeleteLoading(true);
        setDeleteError(null);
        const { ok, error } = await subjects.remove(deleting.id);
        setDeleteLoading(false);

        if (ok) {
            setDeleting(null);
            showSuccess('Asignatura eliminada');
        } else {
            setDeleteError(error);
        }
    };

    return (
        <>
            <PageHeader
                title="Asignaturas"
                subtitle="Codigo unico por plan, horas TEL menores a 8 y prerrequisitos del mismo plan."
                action={
                    <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEditing({})}>
                        Nueva asignatura
                    </Button>
                }
            />

            <ErrorAlert error={subjects.error} onRetry={subjects.reload} onClose={() => subjects.setError(null)} />
            <ErrorAlert error={plans.error} onRetry={plans.reload} />

            <Box sx={{ maxWidth: 360, mb: 2 }}>
                <SelectField
                    label="Plan de estudios"
                    value={planFilter}
                    onChange={(event) => setPlanFilter(event.target.value)}
                    options={(plans.data ?? []).map((plan) => ({
                        value: String(plan.id),
                        label: `${plan.identifier} — ${plan.careerName}`,
                    }))}
                    placeholder="Todos los planes"
                />
            </Box>

            <DataTable
                columns={columns}
                rows={visibleSubjects}
                loading={subjects.loading}
                emptyTitle="No hay asignaturas"
                actions={(row) => (
                    <Stack direction="row" spacing={0.5} sx={{ justifyContent: 'flex-end' }}>
                        <Tooltip title="Prerrequisitos">
                            <IconButton size="small" onClick={() => setPrerequisitesOf(row)}>
                                <AccountTreeIcon fontSize="small" />
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

            <SubjectForm
                open={Boolean(editing)}
                subject={editing?.id ? editing : null}
                studyPlans={plans.data ?? []}
                onSubmit={handleSubmit}
                onClose={() => setEditing(null)}
            />

            <PrerequisitesDialog
                open={Boolean(prerequisitesOf)}
                subject={prerequisitesOf}
                planSubjects={planSubjectsOf(prerequisitesOf)}
                onClose={() => setPrerequisitesOf(null)}
            />

            <ConfirmDialog
                open={Boolean(deleting)}
                title="Eliminar asignatura"
                message={`Se eliminara ${deleting?.code} — ${deleting?.name}. No se puede eliminar si es prerrequisito de otra, tiene secciones, inscripciones o aparece en el historial academico.`}
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
