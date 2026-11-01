import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { blockLabel, dayShort, sortBlocks } from '../../utils/schedule';

/**
 * Horario de una seccion como fichas "Lun M1". Lo comparten las pantallas de
 * secciones, mis secciones e inscripcion, que es el vocabulario de horario de
 * todo el sistema (dia + modulo, nunca una hora del reloj).
 */
export default function ScheduleBlocks({ blocks = [], size = 'small' }) {
    if (blocks.length === 0) {
        return (
            <Typography variant="caption" color="text.secondary">
                Sin horario
            </Typography>
        );
    }

    return (
        <Stack direction="row" spacing={0.5} useFlexGap sx={{ flexWrap: 'wrap' }}>
            {sortBlocks(blocks).map((block) => (
                <Chip
                    key={`${block.day}-${block.module}`}
                    label={`${dayShort(block.day)} M${block.module}`}
                    title={blockLabel(block)}
                    size={size}
                    variant="outlined"
                />
            ))}
        </Stack>
    );
}
