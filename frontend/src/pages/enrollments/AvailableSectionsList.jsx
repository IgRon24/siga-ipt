import Checkbox from '@mui/material/Checkbox';
import Typography from '@mui/material/Typography';
import ScheduleBlocks from '../../components/domain/ScheduleBlocks';
import DataTable from '../../components/ui/DataTable';

/**
 * Oferta disponible para el estudiante en el periodo. Viene de
 * GET /api/enrollments/available, que ya descarta lo aprobado, lo ya inscrito
 * y lo que tiene prerrequisitos pendientes.
 */
export default function AvailableSectionsList({ sections, selectedIds, onToggle, loading, disabled }) {
    const columns = [
        {
            field: 'select',
            label: '',
            width: 56,
            render: (row) => (
                <Checkbox
                    size="small"
                    checked={selectedIds.includes(row.sectionId)}
                    onChange={() => onToggle(row.sectionId)}
                    disabled={disabled || (row.availableSeats ?? 0) <= 0}
                    inputProps={{ 'aria-label': `Seleccionar ${row.subjectCode}` }}
                />
            ),
        },
        {
            field: 'subjectCode',
            label: 'Asignatura',
            render: (row) => (
                <>
                    <Typography variant="body2">{row.subjectCode}</Typography>
                    <Typography variant="caption" color="text.secondary">
                        {row.subjectName}
                    </Typography>
                </>
            ),
        },
        { field: 'subjectSemester', label: 'Sem.', align: 'center', width: 70 },
        { field: 'subjectSct', label: 'SCT', align: 'center', width: 70 },
        { field: 'teacherFullName', label: 'Docente', width: 200 },
        {
            field: 'scheduleBlocks',
            label: 'Horario',
            width: 190,
            render: (row) => <ScheduleBlocks blocks={row.scheduleBlocks} />,
        },
        {
            field: 'availableSeats',
            label: 'Cupos',
            align: 'right',
            width: 110,
            render: (row) =>
                (row.availableSeats ?? 0) > 0 ? (
                    <Typography variant="body2">
                        {row.availableSeats} de {row.capacity}
                    </Typography>
                ) : (
                    <Typography variant="caption" color="error.main">
                        Sin cupos
                    </Typography>
                ),
        },
    ];

    return (
        <DataTable
            columns={columns}
            rows={sections}
            keyField="sectionId"
            loading={loading}
            emptyTitle="No hay asignaturas disponibles"
            emptyDescription="Puede que ya las tengas inscritas o aprobadas, que falten prerrequisitos, o que este periodo no tenga oferta para tu plan. Prueba otro periodo en el selector."
        />
    );
}
