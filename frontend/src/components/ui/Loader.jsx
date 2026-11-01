import Box from '@mui/material/Box';
import CircularProgress from '@mui/material/CircularProgress';
import Typography from '@mui/material/Typography';

export default function Loader({ label = 'Cargando...', minHeight = 160 }) {
    return (
        <Box
            sx={{
                minHeight,
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                gap: 1.5,
            }}
        >
            <CircularProgress size={28} />
            <Typography variant="body2" color="text.secondary">
                {label}
            </Typography>
        </Box>
    );
}
