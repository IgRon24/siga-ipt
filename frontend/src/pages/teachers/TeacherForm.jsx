import { useEffect } from 'react';
import Box from '@mui/material/Box';
import Dialog from '@mui/material/Dialog';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import ErrorAlert from '../../components/ui/ErrorAlert';
import FormActions from '../../components/form/FormActions';
import TextField from '../../components/form/TextField';
import { useApiCall } from '../../hooks/useApiCall';
import { useForm } from '../../hooks/useForm';
import { isValidationError } from '../../utils/httpError';
import { required, validEmail, validRun } from '../../utils/validators';

const EMPTY = { run: '', fullName: '', email: '', professionalTitle: '', academicDegree: '' };

const RULES = {
    run: [required, validRun],
    fullName: [required],
    email: [required, validEmail],
    professionalTitle: [required],
    academicDegree: [required],
};

/** El estado no se recibe: todo docente nace ACTIVO y se cambia con su propia accion. */
export default function TeacherForm({ open, teacher, onSubmit, onClose }) {
    const form = useForm(EMPTY, { rules: RULES });
    const { call, loading, error, reset: resetError } = useApiCall(onSubmit);

    useEffect(() => {
        if (!open) return;
        resetError();
        form.reset(
            teacher
                ? {
                      run: teacher.run ?? '',
                      fullName: teacher.fullName ?? '',
                      email: teacher.email ?? '',
                      professionalTitle: teacher.professionalTitle ?? '',
                      academicDegree: teacher.academicDegree ?? '',
                  }
                : EMPTY,
        );
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [open, teacher]);

    const handleSubmit = async (event) => {
        event.preventDefault();
        if (!form.validate()) return;

        const { ok, error: apiError } = await call(form.values);
        if (ok) {
            onClose();
            return;
        }
        if (isValidationError(apiError)) form.applyFieldErrors(apiError.fieldErrors);
    };

    return (
        <Dialog open={open} onClose={loading ? undefined : onClose} fullWidth maxWidth="sm">
            <DialogTitle>{teacher ? `Editar docente ${teacher.run}` : 'Nuevo docente'}</DialogTitle>
            <DialogContent>
                <Box component="form" onSubmit={handleSubmit} noValidate>
                    <ErrorAlert error={error} />
                    <TextField label="RUN" placeholder="12345678-9" {...form.fieldProps('run')} />
                    <TextField label="Nombre completo" {...form.fieldProps('fullName')} />
                    <TextField
                        label="Email"
                        type="email"
                        helperText="Con este email el docente califica sus secciones"
                        {...form.fieldProps('email')}
                    />
                    <TextField label="Titulo profesional" {...form.fieldProps('professionalTitle')} />
                    <TextField label="Grado academico" {...form.fieldProps('academicDegree')} />
                    <FormActions onCancel={onClose} loading={loading} />
                </Box>
            </DialogContent>
        </Dialog>
    );
}
