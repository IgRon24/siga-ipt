import { useCallback, useState } from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Stack from '@mui/material/Stack';
import StatusChip from '../../components/domain/StatusChip';
import SelectField from '../../components/form/SelectField';
import DataTable from '../../components/ui/DataTable';
import ErrorAlert from '../../components/ui/ErrorAlert';
import { useApiCall } from '../../hooks/useApiCall';
import { useApiData } from '../../hooks/useApiData';
import { useToast } from '../../hooks/useToast';
import { studentPeriodStatusService } from '../../services/studentPeriodStatusService';
import { MATRICULATION_STATUS, statusOptions } from '../../utils/enums';

/**
 * Matricula del estudiante por periodo. Es un PUT idempotente (registra o
 * actualiza) y es requisito para que el estudiante pueda inscribir asignaturas:
 * sin MATRICULATED en el periodo, la inscripcion se rechaza.
 */
export default function MatriculationDialog({ open, student, openPeriods, onClose }) {
    const { showSuccess } = useToast();
    const [periodId, setPeriodId] = useState('');
    const [status, setStatus] = useState('MATRICULATED');

    const loadRecords = useCallback(
        () => (student ? studentPeriodStatusService.byStudent(student.id) : Promise.resolve([])),
        [student],
    );
    const records = useApiData(loadRecords, { auto: Boolean(student) });

    const { call: save, error: saveError, loading: saving, reset: resetSave } = useApiCall(
        useCallback(
            (payload) => studentPeriodStatusService.save(payload),
            [],
        ),
    );

    const columns = [
        { field: 'academicPeriodCode', label: 'Periodo', width: 120 },
        {
            field: 'enrollmentStatus',
            label: 'Matricula',
            render: (row) => <StatusChip kind="matriculation" value={row.enrollmentStatus} />,
        },
    ];

    const handleSave = async () => {
        if (!periodId) return;
        const { ok } = await save({
            studentId: student.id,
            academicPeriodId: Number(periodId),
            enrollmentStatus: status,
        });
        if (ok) {
            showSuccess('Matricula actualizada');
            records.reload();
        }
    };

    return (
        <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
            <DialogTitle>Matricula de {student?.fullName}</DialogTitle>
            <DialogContent>
                <ErrorAlert error={records.error} onRetry={records.reload} />
                <ErrorAlert error={saveError} onClose={resetSave} />

                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ mb: 2, alignItems: 'flex-start' }}>
                    <Box sx={{ flexGrow: 1 }}>
                        <SelectField
                            label="Periodo"
                            value={periodId}
                            onChange={(event) => setPeriodId(event.target.value)}
                            options={openPeriods.map((period) => ({
                                value: String(period.id),
                                label: period.code,
                            }))}
                            placeholder="Selecciona un periodo abierto"
                        />
                    </Box>
                    <Box sx={{ minWidth: 190 }}>
                        <SelectField
                            label="Estado"
                            value={status}
                            onChange={(event) => setStatus(event.target.value)}
                            options={statusOptions(MATRICULATION_STATUS)}
                        />
                    </Box>
                    <Button variant="contained" onClick={handleSave} disabled={!periodId || saving} sx={{ mt: 1 }}>
                        Guardar
                    </Button>
                </Stack>

                <DataTable
                    columns={columns}
                    rows={records.data ?? []}
                    loading={records.loading}
                    emptyTitle="Sin matriculas registradas"
                    emptyDescription="El estudiante no podra inscribir asignaturas hasta que quede MATRICULATED en un periodo abierto."
                />
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>Cerrar</Button>
            </DialogActions>
        </Dialog>
    );
}
