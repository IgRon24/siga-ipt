import { useCallback, useState } from 'react';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import Accordion from '@mui/material/Accordion';
import AccordionDetails from '@mui/material/AccordionDetails';
import AccordionSummary from '@mui/material/AccordionSummary';
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import ScheduleBlocks from '../../components/domain/ScheduleBlocks';
import StatusChip from '../../components/domain/StatusChip';
import DataTable from '../../components/ui/DataTable';
import ErrorAlert from '../../components/ui/ErrorAlert';
import Loader from '../../components/ui/Loader';
import { useApiData } from '../../hooks/useApiData';
import { gradeService } from '../../services/gradeService';
import { formatGrade, formatRun } from '../../utils/formatters';

const studentColumns = [
    { field: 'studentRun', label: 'RUN', width: 140, render: (row) => formatRun(row.studentRun) },
    { field: 'studentFullName', label: 'Estudiante' },
    {
        field: 'value',
        label: 'Nota',
        align: 'right',
        width: 90,
        render: (row) => (
            <Box component="span" sx={{ fontVariantNumeric: 'tabular-nums' }}>
                {formatGrade(row.value)}
            </Box>
        ),
    },
    {
        field: 'result',
        label: 'Resultado',
        width: 150,
        render: (row) =>
            row.result ? (
                <StatusChip kind="result" value={row.result} />
            ) : (
                <Typography variant="caption" color="text.secondary">
                    Sin calificar
                </Typography>
            ),
    },
];

/**
 * Una seccion a cargo del docente. Los estudiantes se cargan al desplegar, no
 * al entrar a la pantalla: son una llamada por seccion.
 *
 * La lista sale del acta (GET /api/grades/section/{id}), que trae una fila por
 * inscripcion INSCRITA e incluye las que todavia no tienen nota. Por eso la
 * tabla se identifica por enrollmentId: el id de la nota viene null mientras no
 * se haya calificado.
 */
export default function SectionAccordion({ section }) {
    const loadSheet = useCallback(() => gradeService.sheet(section.id), [section.id]);
    const sheet = useApiData(loadSheet, { auto: false });
    const [loadedOnce, setLoadedOnce] = useState(false);

    const handleChange = (_event, expanded) => {
        if (expanded && !loadedOnce) {
            setLoadedOnce(true);
            sheet.reload();
        }
    };

    const rows = sheet.data ?? [];
    const graded = rows.filter((row) => row.value !== null && row.value !== undefined).length;

    return (
        <Accordion variant="outlined" onChange={handleChange} disableGutters sx={{ mb: 1 }}>
            <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                <Stack
                    direction={{ xs: 'column', md: 'row' }}
                    spacing={{ xs: 0.5, md: 2 }}
                    sx={{ width: '100%', alignItems: { md: 'center' }, pr: 2 }}
                >
                    <Box sx={{ minWidth: 260, flexGrow: 1 }}>
                        <Typography variant="subtitle2">{section.subjectCode}</Typography>
                        <Typography variant="body2" color="text.secondary">
                            {section.subjectName}
                        </Typography>
                    </Box>

                    <ScheduleBlocks blocks={section.scheduleBlocks} />

                    <Typography variant="body2" color="text.secondary" sx={{ minWidth: 150 }}>
                        {section.academicPeriodCode} · {section.capacity} cupos
                    </Typography>
                </Stack>
            </AccordionSummary>

            <AccordionDetails>
                <ErrorAlert error={sheet.error} onRetry={sheet.reload} />

                {sheet.loading ? (
                    <Loader label="Cargando estudiantes..." minHeight={120} />
                ) : (
                    <>
                        <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                            {rows.length} inscritos de {section.capacity} cupos · {graded} con nota registrada
                        </Typography>
                        <DataTable
                            columns={studentColumns}
                            rows={rows}
                            keyField="enrollmentId"
                            emptyTitle="Sin estudiantes inscritos"
                            emptyDescription="Nadie ha inscrito esta seccion todavia."
                        />
                    </>
                )}
            </AccordionDetails>
        </Accordion>
    );
}
