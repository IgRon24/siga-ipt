import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';

export default function EmptyState({ title = 'Sin datos', description, action }) {
    return (
        <Box sx={{ py: 6, textAlign: 'center', color: 'text.secondary' }}>
            <Typography variant="subtitle1" gutterBottom>
                {title}
            </Typography>
            {description && (
                <Typography variant="body2" sx={{ mb: action ? 2 : 0 }}>
                    {description}
                </Typography>
            )}
            {action}
        </Box>
    );
}
