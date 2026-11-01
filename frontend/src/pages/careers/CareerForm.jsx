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
import { CAREER_STATUS, statusOptions } from '../../utils/enums';
import { isValidationError } from '../../utils/httpError';
import { required, validCareerCode } from '../../utils/validators';

const EMPTY = { code: '', name: '', description: '', status: 'ACTIVA' };

const RULES = {
    code: [required, validCareerCode],
    name: [required],
    description: [required],
    status: [required],
};

/**
 * Formulario de carrera. Muestra el patron que siguen todos los formularios:
 * valida local, envia, y si el backend responde 400 reparte sus fieldErrors en
 * los campos; si responde 409 (regla de negocio) lo muestra arriba.
 *
 * onSubmit viene de la pagina y devuelve {ok, error}.
 */
export default function CareerForm({ open, career, onSubmit, onClose }) {
    const form = useForm(EMPTY, { rules: RULES });
    const { call, loading, error, reset: resetError } = useApiCall(onSubmit);

    useEffect(() => {
        if (!open) return;
        resetError();
        form.reset(
            career
                ? {
                      code: career.code ?? '',
                      name: career.name ?? '',
                      description: career.description ?? '',
                      status: career.status ?? 'ACTIVA',
                  }
                : EMPTY,
        );
        // Solo al abrir o al cambiar la carrera editada
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [open, career]);

    const handleSubmit = async (event) => {
        event.preventDefault();
        if (!form.validate()) return;

        const { ok, error: apiError } = await call(form.values);
        if (ok) {
            onClose();
            return;
        }
        if (isValidationError(apiError)) {
            form.applyFieldErrors(apiError.fieldErrors);
        }
    };

    return (
        <Dialog open={open} onClose={loading ? undefined : onClose} fullWidth maxWidth="sm">
            <DialogTitle>{career ? `Editar carrera ${career.code}` : 'Nueva carrera'}</DialogTitle>
            <DialogContent>
                <Box component="form" onSubmit={handleSubmit} noValidate>
                    <ErrorAlert error={error} />

                    <TextField
                        label="Codigo"
                        placeholder="12345"
                        helperText="Cinco digitos, unico en el sistema"
                        {...form.fieldProps('code')}
                    />
                    <TextField label="Nombre" {...form.fieldProps('name')} />
                    <TextField label="Descripcion" multiline minRows={2} {...form.fieldProps('description')} />
                    <SelectField label="Estado" options={statusOptions(CAREER_STATUS)} {...form.fieldProps('status')} />

                    <FormActions onCancel={onClose} loading={loading} />
                </Box>
            </DialogContent>
        </Dialog>
    );
}
