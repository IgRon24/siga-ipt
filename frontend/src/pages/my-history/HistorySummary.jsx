import Grid from '@mui/material/Grid';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import { formatGrade } from '../../utils/formatters';

/**
 * Resumen del historial en cuatro numeros. Sin grafico: son cuatro magnitudes
 * independientes, no una serie.
 *
 * El valor va en tinta normal, no en color de estado: el verde y el rojo estan
 * reservados para el resultado de cada asignatura, donde van acompanados de su
 * etiqueta en la tabla.
 */
function StatTile({ label, value, hint }) {
    return (
        <Paper variant="outlined" sx={{ p: 2, height: '100%' }}>
            <Typography variant="body2" color="text.secondary" gutterBottom>
                {label}
            </Typography>
            <Typography variant="h4" sx={{ fontWeight: 600, lineHeight: 1.1 }}>
                {value}
            </Typography>
            {hint && (
                <Typography variant="caption" color="text.secondary">
                    {hint}
                </Typography>
            )}
        </Paper>
    );
}

export default function HistorySummary({ total, approved, failed, average }) {
    const percentage = total > 0 ? Math.round((approved / total) * 100) : 0;

    return (
        <Grid container spacing={2} sx={{ mb: 3 }}>
            <Grid size={{ xs: 6, md: 3 }}>
                <StatTile label="Asignaturas cursadas" value={total} />
            </Grid>
            <Grid size={{ xs: 6, md: 3 }}>
                <StatTile label="Aprobadas" value={approved} hint={`${percentage}% de lo cursado`} />
            </Grid>
            <Grid size={{ xs: 6, md: 3 }}>
                <StatTile label="Reprobadas" value={failed} />
            </Grid>
            <Grid size={{ xs: 6, md: 3 }}>
                <StatTile
                    label="Promedio general"
                    value={average === null ? '-' : formatGrade(average)}
                    hint="Escala 1.0 a 7.0"
                />
            </Grid>
        </Grid>
    );
}
