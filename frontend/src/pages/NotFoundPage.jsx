import { Link as RouterLink } from 'react-router-dom';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';

export default function NotFoundPage() {
    return (
        <Box sx={{ textAlign: 'center', py: 8 }}>
            <Typography variant="h4" gutterBottom>
                404
            </Typography>
            <Typography color="text.secondary" sx={{ mb: 3 }}>
                La pagina que buscas no existe.
            </Typography>
            <Button component={RouterLink} to="/dashboard" variant="contained">
                Volver al inicio
            </Button>
        </Box>
    );
}
