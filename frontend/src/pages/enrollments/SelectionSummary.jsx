import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Divider from '@mui/material/Divider';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { MAX_SUBJECTS_PER_PERIOD } from '../../utils/validators';

/**
 * Resumen de la seleccion: cuanto llevas, cuanto exige el periodo y que esta
 * impidiendo enviar. La inscripcion se envia completa, de una sola vez, porque
 * el minimo y el maximo solo tienen sentido sobre el conjunto.
 */
export default function SelectionSummary({ evaluation, enrolledCount, onSubmit, submitting, disabledReason }) {
    const { selected, totalSubjects, totalSct, minimum, blockers, canSubmit } = evaluation;

    return (
        <Paper variant="outlined" sx={{ p: 2, position: { md: 'sticky' }, top: { md: 88 } }}>
            <Typography variant="subtitle1" gutterBottom>
                Tu seleccion
            </Typography>

            <Stack spacing={0.5} sx={{ mb: 2 }}>
                <Row label="Ya inscritas" value={enrolledCount} />
                <Row label="Seleccionadas" value={selected.length} />
                <Divider sx={{ my: 1 }} />
                <Row label="Total en el periodo" value={`${totalSubjects} de ${MAX_SUBJECTS_PER_PERIOD}`} strong />
                <Row label="Minimo exigido" value={minimum} />
                <Row label="SCT seleccionados" value={totalSct} />
            </Stack>

            {selected.length > 0 && (
                <Box sx={{ mb: 2 }}>
                    {selected.map((section) => (
                        <Typography key={section.sectionId} variant="body2" color="text.secondary">
                            {section.subjectCode} — {section.subjectName}
                        </Typography>
                    ))}
                </Box>
            )}

            {blockers.length > 0 && (
                <Alert severity="warning" sx={{ mb: 2 }}>
                    <AlertTitle>Revisa tu seleccion</AlertTitle>
                    <Box component="ul" sx={{ pl: 2.5, mb: 0 }}>
                        {blockers.map((blocker) => (
                            <li key={blocker}>{blocker}</li>
                        ))}
                    </Box>
                </Alert>
            )}

            {disabledReason && (
                <Alert severity="info" sx={{ mb: 2 }}>
                    {disabledReason}
                </Alert>
            )}

            <Button
                variant="contained"
                fullWidth
                size="large"
                onClick={onSubmit}
                disabled={!canSubmit || submitting || Boolean(disabledReason)}
            >
                {submitting ? 'Inscribiendo...' : `Inscribir ${selected.length || ''}`.trim()}
            </Button>
        </Paper>
    );
}

function Row({ label, value, strong }) {
    return (
        <Stack direction="row" sx={{ justifyContent: 'space-between' }}>
            <Typography variant="body2" color="text.secondary">
                {label}
            </Typography>
            <Typography variant="body2" sx={{ fontWeight: strong ? 600 : 400 }}>
                {value}
            </Typography>
        </Stack>
    );
}
