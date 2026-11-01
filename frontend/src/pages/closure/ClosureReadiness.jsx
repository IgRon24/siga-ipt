import Typography from '@mui/material/Typography';
import ScheduleBlocks from '../../components/domain/ScheduleBlocks';
import DataTable from '../../components/ui/DataTable';

/**
 * Estado de cada seccion del periodo frente al cierre. Lo que importa es la
 * columna de pendientes: el cierre se rechaza mientras exista una inscripcion
 * INSCRITA sin nota, y el 409 del backend lista justamente esas asignaturas.
 */
export default function ClosureReadiness({ rows, loading }) {
    const columns = [
        {
            field: 'subjectCode',
            label: 'Asignatura',
            render: (row) => (
                <>
                    <Typography variant="body2">{row.section.subjectCode}</Typography>
                    <Typography variant="caption" color="text.secondary">
                        {row.section.subjectName}
                    </Typography>
                </>
            ),
        },
        { field: 'teacher', label: 'Docente', width: 200, render: (row) => row.section.teacherFullName },
        {
            field: 'schedule',
            label: 'Horario',
            width: 180,
            render: (row) => <ScheduleBlocks blocks={row.section.scheduleBlocks} />,
        },
        { field: 'enrolled', label: 'Inscritos', align: 'center', width: 100 },
        { field: 'graded', label: 'Con nota', align: 'center', width: 100 },
        {
            field: 'pending',
            label: 'Pendientes',
            align: 'center',
            width: 110,
            render: (row) =>
                row.pending > 0 ? (
                    <Typography variant="body2" color="error.main" sx={{ fontWeight: 600 }}>
                        {row.pending}
                    </Typography>
                ) : (
                    <Typography variant="body2" color="success.main">
                        0
                    </Typography>
                ),
        },
    ];

    return (
        <DataTable
            columns={columns}
            rows={rows}
            keyField="id"
            loading={loading}
            emptyTitle="El periodo no tiene secciones"
            emptyDescription="Un periodo sin oferta se puede cerrar, pero no consolidara ningun registro."
        />
    );
}
