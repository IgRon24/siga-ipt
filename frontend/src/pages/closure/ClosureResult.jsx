import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Grid from '@mui/material/Grid';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { formatRun } from '../../utils/formatters';

/**
 * Resumen que devuelve el cierre. Los dos ultimos bloques son los que tienen
 * consecuencias permanentes: EGRESADO y ELIMINADO son estados terminales que
 * solo asigna este proceso.
 */
export default function ClosureResult({ result, onDismiss }) {
    return (
        <Alert severity="success" sx={{ mb: 3 }} onClose={onDismiss}>
            <AlertTitle>Periodo {result.academicPeriodCode} cerrado</AlertTitle>

            <Grid container spacing={2} sx={{ mt: 1, mb: 1 }}>
                <Tile label="Inscripciones consolidadas" value={result.consolidatedEnrollments} />
                <Tile label="Aprobadas" value={result.approvedCount} />
                <Tile label="Reprobadas" value={result.failedCount} />
            </Grid>

            <RunList
                title="Estudiantes egresados"
                description="Aprobaron todas las asignaturas de su plan."
                runs={result.graduatedStudentRuns}
            />
            <RunList
                title="Estudiantes eliminados"
                description="Segunda reprobacion de la misma asignatura."
                runs={result.eliminatedStudentRuns}
            />

            <Typography variant="body2" sx={{ mt: 1 }}>
                El historial academico ya quedo escrito y el periodo no se puede reabrir.
            </Typography>
        </Alert>
    );
}

function Tile({ label, value }) {
    return (
        <Grid size={{ xs: 12, sm: 4 }}>
            <Paper variant="outlined" sx={{ p: 1.5 }}>
                <Typography variant="caption" color="text.secondary">
                    {label}
                </Typography>
                <Typography variant="h5" sx={{ fontWeight: 600 }}>
                    {value}
                </Typography>
            </Paper>
        </Grid>
    );
}

function RunList({ title, description, runs }) {
    if (!runs || runs.length === 0) return null;
    return (
        <Box sx={{ mt: 1.5 }}>
            <Typography variant="subtitle2">{title}</Typography>
            <Typography variant="caption" color="text.secondary">
                {description}
            </Typography>
            <Stack direction="row" spacing={0.5} useFlexGap sx={{ flexWrap: 'wrap', mt: 0.5 }}>
                {runs.map((run) => (
                    <Chip key={run} label={formatRun(run)} size="small" />
                ))}
            </Stack>
        </Box>
    );
}
