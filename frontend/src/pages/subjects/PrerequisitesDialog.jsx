import { useCallback, useMemo, useState } from 'react';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import IconButton from '@mui/material/IconButton';
import Stack from '@mui/material/Stack';
import SelectField from '../../components/form/SelectField';
import DataTable from '../../components/ui/DataTable';
import ErrorAlert from '../../components/ui/ErrorAlert';
import { useApiData } from '../../hooks/useApiData';
import { useApiCall } from '../../hooks/useApiCall';
import { useToast } from '../../hooks/useToast';
import { subjectService } from '../../services/subjectService';

/**
 * Prerrequisitos de una asignatura (subrecurso /api/subjects/{id}/prerequisites).
 *
 * El backend exige que el prerrequisito sea del mismo plan y rechaza los ciclos;
 * aqui solo se ofrecen las asignaturas del plan, distintas de ella misma y que
 * no esten ya agregadas.
 */
export default function PrerequisitesDialog({ open, subject, planSubjects, onClose }) {
    const { showSuccess } = useToast();
    const [toAdd, setToAdd] = useState('');

    const loadPrerequisites = useCallback(
        () => (subject ? subjectService.listPrerequisites(subject.id) : Promise.resolve([])),
        [subject],
    );
    const prerequisites = useApiData(loadPrerequisites, { auto: Boolean(subject) });

    const { call: add, error: addError, loading: adding, reset: resetAdd } = useApiCall(
        useCallback(
            (prerequisiteSubjectId) => subjectService.addPrerequisite(subject.id, prerequisiteSubjectId),
            [subject],
        ),
    );
    const { call: remove, error: removeError, reset: resetRemove } = useApiCall(
        useCallback(
            (prerequisiteSubjectId) => subjectService.removePrerequisite(subject.id, prerequisiteSubjectId),
            [subject],
        ),
    );

    const rows = useMemo(() => prerequisites.data ?? [], [prerequisites.data]);

    const options = useMemo(() => {
        const taken = new Set(rows.map((row) => row.prerequisiteSubjectId));
        return planSubjects
            .filter((candidate) => candidate.id !== subject?.id && !taken.has(candidate.id))
            .map((candidate) => ({
                value: String(candidate.id),
                label: `${candidate.code} — ${candidate.name} (sem. ${candidate.semester})`,
            }));
    }, [planSubjects, rows, subject]);

    const columns = [
        { field: 'prerequisiteSubjectCode', label: 'Codigo', width: 150 },
        { field: 'prerequisiteSubjectName', label: 'Asignatura' },
        { field: 'prerequisiteSubjectSemester', label: 'Sem.', align: 'center', width: 70 },
    ];

    const handleAdd = async () => {
        if (!toAdd) return;
        const { ok } = await add(Number(toAdd));
        if (ok) {
            setToAdd('');
            showSuccess('Prerrequisito agregado');
            prerequisites.reload();
        }
    };

    const handleRemove = async (row) => {
        const { ok } = await remove(row.prerequisiteSubjectId);
        if (ok) {
            showSuccess('Prerrequisito eliminado');
            prerequisites.reload();
        }
    };

    return (
        <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
            <DialogTitle>Prerrequisitos de {subject?.code}</DialogTitle>
            <DialogContent>
                <ErrorAlert error={prerequisites.error} onRetry={prerequisites.reload} />
                <ErrorAlert error={addError} onClose={resetAdd} />
                <ErrorAlert error={removeError} onClose={resetRemove} />

                <Stack direction="row" spacing={1} sx={{ mb: 2, alignItems: 'flex-start' }}>
                    <Box sx={{ flexGrow: 1 }}>
                        <SelectField
                            label="Agregar prerrequisito"
                            value={toAdd}
                            onChange={(event) => setToAdd(event.target.value)}
                            options={options}
                            placeholder="Selecciona una asignatura del plan"
                        />
                    </Box>
                    <Button
                        variant="contained"
                        startIcon={<AddIcon />}
                        onClick={handleAdd}
                        disabled={!toAdd || adding}
                        sx={{ mt: 1 }}
                    >
                        Agregar
                    </Button>
                </Stack>

                <DataTable
                    columns={columns}
                    rows={rows}
                    loading={prerequisites.loading}
                    emptyTitle="Sin prerrequisitos"
                    emptyDescription="La asignatura se puede inscribir sin aprobar nada antes."
                    actions={(row) => (
                        <IconButton size="small" onClick={() => handleRemove(row)}>
                            <DeleteIcon fontSize="small" />
                        </IconButton>
                    )}
                />
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>Cerrar</Button>
            </DialogActions>
        </Dialog>
    );
}
