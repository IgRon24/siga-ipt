import { Fragment } from 'react';
import CheckIcon from '@mui/icons-material/Check';
import Box from '@mui/material/Box';
import ToggleButton from '@mui/material/ToggleButton';
import Typography from '@mui/material/Typography';
import { DAYS, MODULES } from '../../utils/schedule';

/**
 * Grilla dia x modulo para armar el horario de una seccion.
 *
 * La cantidad exacta de bloques la manda la asignatura: (T + E + L) / 2, con
 * dos horas pedagogicas por bloque. El contador muestra cuanto falta; el
 * backend rechaza con 409 cualquier horario que no calce o que choque con otra
 * seccion del mismo docente en el periodo.
 */
export default function ScheduleEditor({ blocks = [], onChange, requiredCount, error }) {
    const isSelected = (day, module) => blocks.some((block) => block.day === day && block.module === module);

    const toggle = (day, module) => {
        onChange(
            isSelected(day, module)
                ? blocks.filter((block) => !(block.day === day && block.module === module))
                : [...blocks, { day, module }],
        );
    };

    const complete = blocks.length === requiredCount;

    return (
        <Box sx={{ mt: 1 }}>
            <Typography variant="body2" gutterBottom>
                Horario
            </Typography>

            <Box
                sx={{
                    display: 'grid',
                    gridTemplateColumns: `44px repeat(${DAYS.length}, 1fr)`,
                    gap: 0.5,
                    alignItems: 'center',
                }}
            >
                <Box />
                {DAYS.map((day) => (
                    <Typography key={day.value} variant="caption" align="center" color="text.secondary">
                        {day.short}
                    </Typography>
                ))}

                {MODULES.map((module) => (
                    <Fragment key={module}>
                        <Typography variant="caption" color="text.secondary">
                            M{module}
                        </Typography>
                        {DAYS.map((day) => (
                            <ToggleButton
                                key={`${day.value}-${module}`}
                                value={`${day.value}-${module}`}
                                selected={isSelected(day.value, module)}
                                onChange={() => toggle(day.value, module)}
                                size="small"
                                sx={{ py: 0.25, minHeight: 32 }}
                                aria-label={`${day.label} modulo ${module}`}
                            >
                                {isSelected(day.value, module) ? <CheckIcon fontSize="small" /> : <Box sx={{ height: 20 }} />}
                            </ToggleButton>
                        ))}
                    </Fragment>
                ))}
            </Box>

            <Typography
                variant="caption"
                color={error ? 'error.main' : complete ? 'success.main' : 'text.secondary'}
                sx={{ display: 'block', mt: 1 }}
            >
                {error ?? `${blocks.length} de ${requiredCount} bloques seleccionados`}
            </Typography>
        </Box>
    );
}
