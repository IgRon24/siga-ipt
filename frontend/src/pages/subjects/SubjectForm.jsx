import { useEffect } from 'react';
import Box from '@mui/material/Box';
import Dialog from '@mui/material/Dialog';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Grid from '@mui/material/Grid';
import Typography from '@mui/material/Typography';
import ErrorAlert from '../../components/ui/ErrorAlert';
import FormActions from '../../components/form/FormActions';
import SelectField from '../../components/form/SelectField';
import TextField from '../../components/form/TextField';
import { useApiCall } from '../../hooks/useApiCall';
import { useForm } from '../../hooks/useForm';
import { isValidationError } from '../../utils/httpError';
import { requiredBlocks } from '../../utils/schedule';
import { inRange, required, validTelHours } from '../../utils/validators';

const EMPTY = {
    code: '',
    name: '',
    semester: 1,
    telTheory: 2,
    telExercises: 2,
    telLab: 0,
    sct: 5,
    studyPlanId: '',
};

const RULES = {
    code: [required],
    name: [required],
    semester: [required, (value) => inRange(value, 1, 4, 'El semestre va de 1 a 4')],
    telTheory: [required, (value) => inRange(value, 0, 7), (_value, values) => validTelHours(values)],
    telExercises: [required, (value) => inRange(value, 0, 7), (_value, values) => validTelHours(values)],
    telLab: [required, (value) => inRange(value, 0, 7), (_value, values) => validTelHours(values)],
    sct: [required, (value) => inRange(value, 1, 7, 'El SCT va de 1 a 7')],
    studyPlanId: [required],
};

const SEMESTERS = [1, 2, 3, 4].map((value) => ({ value, label: `Semestre ${value}` }));

/**
 * Las horas TEL definen el horario: la seccion de esta asignatura va a exigir
 * exactamente (teoria + ejercicios + laboratorio) / 2 bloques, y la suma debe
 * ser menor que 8.
 */
export default function SubjectForm({ open, subject, studyPlans, onSubmit, onClose }) {
    const form = useForm(EMPTY, { rules: RULES });
    const { call, loading, error, reset: resetError } = useApiCall(onSubmit);

    useEffect(() => {
        if (!open) return;
        resetError();
        form.reset(
            subject
                ? {
                      code: subject.code ?? '',
                      name: subject.name ?? '',
                      semester: subject.semester ?? 1,
                      telTheory: subject.telTheory ?? 0,
                      telExercises: subject.telExercises ?? 0,
                      telLab: subject.telLab ?? 0,
                      sct: subject.sct ?? 1,
                      studyPlanId: String(subject.studyPlanId ?? ''),
                  }
                : EMPTY,
        );
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [open, subject]);

    const totalTel =
        Number(form.values.telTheory || 0) +
        Number(form.values.telExercises || 0) +
        Number(form.values.telLab || 0);
    const blocks = requiredBlocks({
        telTheory: form.values.telTheory,
        telExercises: form.values.telExercises,
        telLab: form.values.telLab,
    });

    const handleSubmit = async (event) => {
        event.preventDefault();
        if (!form.validate()) return;

        const { ok, error: apiError } = await call({
            code: form.values.code,
            name: form.values.name,
            semester: Number(form.values.semester),
            telTheory: Number(form.values.telTheory),
            telExercises: Number(form.values.telExercises),
            telLab: Number(form.values.telLab),
            sct: Number(form.values.sct),
            studyPlanId: Number(form.values.studyPlanId),
        });
        if (ok) {
            onClose();
            return;
        }
        if (isValidationError(apiError)) form.applyFieldErrors(apiError.fieldErrors);
    };

    return (
        <Dialog open={open} onClose={loading ? undefined : onClose} fullWidth maxWidth="sm">
            <DialogTitle>{subject ? `Editar asignatura ${subject.code}` : 'Nueva asignatura'}</DialogTitle>
            <DialogContent>
                <Box component="form" onSubmit={handleSubmit} noValidate>
                    <ErrorAlert error={error} />

                    <SelectField
                        label="Plan de estudios"
                        options={studyPlans.map((plan) => ({
                            value: String(plan.id),
                            label: `${plan.identifier} — ${plan.careerName}`,
                        }))}
                        {...form.fieldProps('studyPlanId')}
                    />
                    <TextField
                        label="Codigo"
                        helperText="Unico dentro del plan de estudios"
                        {...form.fieldProps('code')}
                    />
                    <TextField label="Nombre" {...form.fieldProps('name')} />

                    <Grid container spacing={1}>
                        <Grid size={{ xs: 6 }}>
                            <SelectField label="Semestre" options={SEMESTERS} {...form.fieldProps('semester')} />
                        </Grid>
                        <Grid size={{ xs: 6 }}>
                            <TextField label="SCT" type="number" {...form.fieldProps('sct')} />
                        </Grid>
                        <Grid size={{ xs: 4 }}>
                            <TextField label="Teoria" type="number" {...form.fieldProps('telTheory')} />
                        </Grid>
                        <Grid size={{ xs: 4 }}>
                            <TextField label="Ejercicios" type="number" {...form.fieldProps('telExercises')} />
                        </Grid>
                        <Grid size={{ xs: 4 }}>
                            <TextField label="Laboratorio" type="number" {...form.fieldProps('telLab')} />
                        </Grid>
                    </Grid>

                    <Typography variant="caption" color={totalTel < 8 ? 'text.secondary' : 'error.main'}>
                        Total TEL: {totalTel} horas (debe ser menor que 8) · su seccion exigira {blocks}{' '}
                        {blocks === 1 ? 'bloque' : 'bloques'} de horario
                    </Typography>

                    <FormActions onCancel={onClose} loading={loading} />
                </Box>
            </DialogContent>
        </Dialog>
    );
}
