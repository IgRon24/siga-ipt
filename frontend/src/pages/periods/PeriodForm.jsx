import { useEffect } from 'react';
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
import { isValidationError } from '../../utils/httpError';
import { inRange, required } from '../../utils/validators';

const EMPTY = { year: new Date().getFullYear(), semester: 1 };

const RULES = {
    year: [required, (value) => inRange(value, 2000, 2100, 'El anio debe estar entre 2000 y 2100')],
    semester: [required],
};

const SEMESTERS = [
    { value: 1, label: '1er semestre' },
    { value: 2, label: '2do semestre' },
];

/**
 * Anio y semestre: el codigo ("2026-1") lo arma el backend y el estado no se
 * recibe, todo periodo nace ABIERTO.
 */
export default function PeriodForm({ open, period, onSubmit, onClose }) {
    const form = useForm(EMPTY, { rules: RULES });
    const { call, loading, error, reset: resetError } = useApiCall(onSubmit);

    useEffect(() => {
        if (!open) return;
        resetError();
        form.reset(period ? { year: period.year, semester: period.semester } : EMPTY);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [open, period]);

    const handleSubmit = async (event) => {
        event.preventDefault();
        if (!form.validate()) return;

        const { ok, error: apiError } = await call({
            year: Number(form.values.year),
            semester: Number(form.values.semester),
        });
        if (ok) {
            onClose();
            return;
        }
        if (isValidationError(apiError)) form.applyFieldErrors(apiError.fieldErrors);
    };

    return (
        <Dialog open={open} onClose={loading ? undefined : onClose} fullWidth maxWidth="xs">
            <DialogTitle>{period ? `Editar periodo ${period.code}` : 'Nuevo periodo'}</DialogTitle>
            <DialogContent>
                <Box component="form" onSubmit={handleSubmit} noValidate>
                    <ErrorAlert error={error} />
                    <TextField label="Anio" type="number" {...form.fieldProps('year')} />
                    <SelectField label="Semestre" options={SEMESTERS} {...form.fieldProps('semester')} />
                    <FormActions onCancel={onClose} loading={loading} />
                </Box>
            </DialogContent>
        </Dialog>
    );
}
