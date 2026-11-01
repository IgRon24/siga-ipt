import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import StatusChip from '../../components/domain/StatusChip';
import DataTable from '../../components/ui/DataTable';
import { formatGrade, formatPeriodCode } from '../../utils/formatters';

const columns = [
    { field: 'subjectCode', label: 'Codigo', width: 160 },
    { field: 'subjectName', label: 'Asignatura' },
    {
        field: 'grade',
        label: 'Nota',
        align: 'right',
        width: 90,
        // Columna de numeros: aqui si conviene alinear los digitos
        render: (row) => (
            <Box component="span" sx={{ fontVariantNumeric: 'tabular-nums' }}>
                {formatGrade(row.grade)}
            </Box>
        ),
    },
    {
        field: 'result',
        label: 'Resultado',
        width: 140,
        render: (row) => <StatusChip kind="result" value={row.result} />,
    },
];

/** Un periodo cerrado con sus asignaturas, tal como quedaron en el snapshot. */
export default function PeriodHistoryCard({ period }) {
    return (
        <Box sx={{ mb: 3 }}>
            <Stack
                direction={{ xs: 'column', sm: 'row' }}
                spacing={1}
                sx={{ mb: 1, alignItems: { sm: 'baseline' }, justifyContent: 'space-between' }}
            >
                <Typography variant="subtitle1">{formatPeriodCode(period.code)}</Typography>
                <Typography variant="body2" color="text.secondary">
                    {period.approved} aprobadas · {period.failed} reprobadas · promedio{' '}
                    {formatGrade(period.average)}
                </Typography>
            </Stack>

            <DataTable columns={columns} rows={period.records} />
        </Box>
    );
}
