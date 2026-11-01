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
import { requiredBlocks, validateSchedule } from '../../utils/schedule';
import { MAX_SECTION_CAPACITY, MIN_SECTION_CAPACITY, inRange, required } from '../../utils/validators';
import ScheduleEditor from './ScheduleEditor';

const EMPTY = { subjectId: '', academicPeriodId: '', teacherId: '', capacity: 30, scheduleBlocks: [] };

const RULES = {
    subjectId: [required],
    academicPeriodId: [required],
    teacherId: [required],
    capacity: [
        required,
        (value) =>
            inRange(
                value,
                MIN_SECTION_CAPACITY,
                MAX_SECTION_CAPACITY,
                `La capacidad va de ${MIN_SECTION_CAPACITY} a ${MAX_SECTION_CAPACITY}`,
            ),
    ],
};

/**
 * Una seccion por asignatura y periodo, con docente ACTIVO y un horario de
 * (T+E+L)/2 bloques. Solo se ofrecen periodos ABIERTO: la oferta de un periodo
 * cerrado no se puede tocar.
 */
export default function SectionForm({ open, section, subjects, periods, teachers, onSubmit, onClose }) {
    const form = useForm(EMPTY, { rules: RULES });
    const { call, loading, error, reset: resetError } = useApiCall(onSubmit);

    useEffect(() => {
        if (!open) return;
        resetError();
        form.reset(
            section
                ? {
                      subjectId: String(section.subjectId ?? ''),
                      academicPeriodId: String(section.academicPeriodId ?? ''),
                      teacherId: String(section.teacherId ?? ''),
                      capacity: section.capacity ?? 30,
                      scheduleBlocks: (section.scheduleBlocks ?? []).map((block) => ({
                          day: block.day,
                          module: block.module,
                      })),
                  }
                : EMPTY,
        );
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [open, section]);

    const subject = subjects.find((item) => String(item.id) === String(form.values.subjectId)) ?? null;
    const neededBlocks = subject ? requiredBlocks(subject) : 0;
    const scheduleError = subject ? validateSchedule(subject, form.values.scheduleBlocks) : null;

    const handleSubmit = async (event) => {
        event.preventDefault();
        if (!form.validate()) return;
        if (scheduleError) return;

        const { ok, error: apiError } = await call({
            subjectId: Number(form.values.subjectId),
            academicPeriodId: Number(form.values.academicPeriodId),
            teacherId: Number(form.values.teacherId),
            capacity: Number(form.values.capacity),
            scheduleBlocks: form.values.scheduleBlocks,
        });
        if (ok) {
            onClose();
            return;
        }
        if (isValidationError(apiError)) form.applyFieldErrors(apiError.fieldErrors);
    };

    return (
        <Dialog open={open} onClose={loading ? undefined : onClose} fullWidth maxWidth="sm">
            <DialogTitle>
                {section ? `Editar seccion de ${section.subjectCode}` : 'Nueva seccion'}
            </DialogTitle>
            <DialogContent>
                <Box component="form" onSubmit={handleSubmit} noValidate>
                    <ErrorAlert error={error} />

                    <SelectField
                        label="Asignatura"
                        options={subjects.map((item) => ({
                            value: String(item.id),
                            label: `${item.code} — ${item.name} (${item.studyPlanName})`,
                        }))}
                        {...form.fieldProps('subjectId')}
                    />
                    <SelectField
                        label="Periodo academico"
                        options={periods.map((item) => ({ value: String(item.id), label: item.code }))}
                        helperText="Solo periodos abiertos"
                        {...form.fieldProps('academicPeriodId')}
                    />
                    <SelectField
                        label="Docente responsable"
                        options={teachers.map((item) => ({
                            value: String(item.id),
                            label: `${item.fullName} (${item.status})`,
                        }))}
                        helperText="Debe estar ACTIVO al momento de asignarlo"
                        {...form.fieldProps('teacherId')}
                    />
                    <TextField label="Capacidad" type="number" {...form.fieldProps('capacity')} />

                    <ScheduleEditor
                        blocks={form.values.scheduleBlocks}
                        onChange={(blocks) => form.setValue('scheduleBlocks', blocks)}
                        requiredCount={neededBlocks}
                        error={subject ? scheduleError : 'Elige primero la asignatura: sus horas TEL definen el horario'}
                    />

                    <FormActions onCancel={onClose} loading={loading} disabled={Boolean(scheduleError)} />
                </Box>
            </DialogContent>
        </Dialog>
    );
}
