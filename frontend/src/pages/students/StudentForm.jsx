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
import { isValidationError } from '../../utils/httpError';
import { required, validEmail, validRun } from '../../utils/validators';

const EMPTY = { run: '', fullName: '', email: '', careerId: '' };

const RULES = {
    run: [required, validRun],
    fullName: [required],
    email: [required, validEmail],
    careerId: [required],
};

/**
 * El plan de estudios no se envia: el backend asigna el VIGENTE de la carrera
 * al registrar, y el estudiante lo conserva aunque luego salga un plan nuevo.
 * El estado tampoco: nace REGULAR.
 */
export default function StudentForm({ open, student, careers, onSubmit, onClose }) {
    const form = useForm(EMPTY, { rules: RULES });
    const { call, loading, error, reset: resetError } = useApiCall(onSubmit);

    useEffect(() => {
        if (!open) return;
        resetError();
        form.reset(
            student
                ? {
                      run: student.run ?? '',
                      fullName: student.fullName ?? '',
                      email: student.email ?? '',
                      careerId: String(student.careerId ?? ''),
                  }
                : EMPTY,
        );
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [open, student]);

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
            <DialogTitle>{student ? `Editar estudiante ${student.run}` : 'Nuevo estudiante'}</DialogTitle>
            <DialogContent>
                <Box component="form" onSubmit={handleSubmit} noValidate>
                    <ErrorAlert error={error} />

                    {student ? (
                        <Alert severity="info" sx={{ mb: 1 }}>
                            Plan asignado: {student.studyPlanIdentifier}. Si el estudiante ya tiene inscripciones o
                            historial, su carrera y su plan quedan congelados.
                        </Alert>
                    ) : (
                        <Alert severity="info" sx={{ mb: 1 }}>
                            Quedara REGULAR y en el plan VIGENTE de la carrera que elijas.
                        </Alert>
                    )}

                    <TextField label="RUN" placeholder="12345678-9" {...form.fieldProps('run')} />
                    <TextField label="Nombre completo" {...form.fieldProps('fullName')} />
                    <TextField
                        label="Email"
                        type="email"
                        helperText="Con este email el estudiante inscribe y consulta lo suyo"
                        {...form.fieldProps('email')}
                    />
                    <SelectField
                        label="Carrera"
                        options={careers.map((career) => ({
                            value: String(career.id),
                            label: `${career.code} — ${career.name}`,
                        }))}
                        {...form.fieldProps('careerId')}
                    />

                    <FormActions onCancel={onClose} loading={loading} />
                </Box>
            </DialogContent>
        </Dialog>
    );
}
