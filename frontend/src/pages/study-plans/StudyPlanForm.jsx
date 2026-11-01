import { useEffect } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Dialog from '@mui/material/Dialog';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import ErrorAlert from '../../components/ui/ErrorAlert';
import FormActions from '../../components/form/FormActions';
import SelectField from '../../components/form/SelectField';
import TextField from '../../components/form/TextField';
import { useApiCall } from '../../hooks/useApiCall';
import { useForm } from '../../hooks/useForm';
import { STUDY_PLAN_STATUS, statusOptions } from '../../utils/enums';
import { isValidationError } from '../../utils/httpError';
import { required } from '../../utils/validators';

const EMPTY = { identifier: '', name: '', status: 'NO_VIGENTE', careerId: '' };

const RULES = {
    identifier: [required],
    name: [required],
    status: [required],
    careerId: [required],
};

export default function StudyPlanForm({ open, plan, careers, onSubmit, onClose }) {
    const form = useForm(EMPTY, { rules: RULES });
    const { call, loading, error, reset: resetError } = useApiCall(onSubmit);

    useEffect(() => {
        if (!open) return;
        resetError();
        form.reset(
            plan
                ? {
                      identifier: plan.identifier ?? '',
                      name: plan.name ?? '',
                      status: plan.status ?? 'NO_VIGENTE',
                      careerId: String(plan.careerId ?? ''),
                  }
                : EMPTY,
        );
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [open, plan]);

    const handleSubmit = async (event) => {
        event.preventDefault();
        if (!form.validate()) return;

        const { ok, error: apiError } = await call({
            ...form.values,
            careerId: Number(form.values.careerId),
        });
        if (ok) {
            onClose();
            return;
        }
        if (isValidationError(apiError)) form.applyFieldErrors(apiError.fieldErrors);
    };

    return (
        <Dialog open={open} onClose={loading ? undefined : onClose} fullWidth maxWidth="sm">
            <DialogTitle>{plan ? `Editar plan ${plan.identifier}` : 'Nuevo plan de estudios'}</DialogTitle>
            <DialogContent>
                <Box component="form" onSubmit={handleSubmit} noValidate>
                    <ErrorAlert error={error} />

                    {form.values.status === 'VIGENTE' && (
                        <Alert severity="info" sx={{ mb: 1 }}>
                            Al quedar VIGENTE, el plan vigente actual de esa carrera pasa a NO_VIGENTE.
                        </Alert>
                    )}

                    <SelectField
                        label="Carrera"
                        options={careers.map((career) => ({
                            value: String(career.id),
                            label: `${career.code} — ${career.name}`,
                        }))}
                        {...form.fieldProps('careerId')}
                    />
                    <TextField
                        label="Identificador"
                        placeholder="P2024"
                        helperText="Unico dentro de la carrera, maximo 20 caracteres"
                        {...form.fieldProps('identifier')}
                    />
                    <TextField label="Nombre" {...form.fieldProps('name')} />
                    <SelectField
                        label="Estado"
                        options={statusOptions(STUDY_PLAN_STATUS)}
                        {...form.fieldProps('status')}
                    />

                    <FormActions onCancel={onClose} loading={loading} />
                </Box>
            </DialogContent>
        </Dialog>
    );
}
