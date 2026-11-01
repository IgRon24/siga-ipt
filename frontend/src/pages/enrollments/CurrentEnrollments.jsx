import CancelIcon from '@mui/icons-material/Cancel';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import ScheduleBlocks from '../../components/domain/ScheduleBlocks';
import StatusChip from '../../components/domain/StatusChip';
import DataTable from '../../components/ui/DataTable';

/**
 * Lo que el estudiante ya tiene en el periodo, incluidas las anuladas: anular
 * no borra la inscripcion, la deja en ANULADA, y el cupo se libera porque los
 * conteos del backend solo miran las INSCRITA.
 */
export default function CurrentEnrollments({ enrollments, loading, onCancel, canCancel }) {
    const columns = [
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
        { field: 'teacherFullName', label: 'Docente', width: 200 },
        {
            field: 'scheduleBlocks',
            label: 'Horario',
            width: 190,
            render: (row) => <ScheduleBlocks blocks={row.scheduleBlocks} />,
        },
        {
            field: 'status',
            label: 'Estado',
            width: 120,
            render: (row) => <StatusChip kind="enrollment" value={row.status} />,
        },
    ];

    return (
        <DataTable
            columns={columns}
            rows={enrollments}
            loading={loading}
            emptyTitle="Todavia no tienes inscripciones en este periodo"
            emptyDescription="Selecciona tus asignaturas de la oferta y envialas juntas."
            actions={(row) =>
                canCancel && row.status === 'INSCRITA' ? (
                    <Tooltip title="Anular inscripcion">
                        <IconButton size="small" onClick={() => onCancel(row)}>
                            <CancelIcon fontSize="small" />
                        </IconButton>
                    </Tooltip>
                ) : null
            }
        />
    );
}
