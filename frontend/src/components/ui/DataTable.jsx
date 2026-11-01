import Paper from '@mui/material/Paper';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import EmptyState from './EmptyState';
import Loader from './Loader';

/**
 * Tabla declarativa, la pieza que reusan todas las pantallas de gestion:
 *
 *   columns = [{ field, label, align, width, render: (row) => node }]
 *   actions = (row) => <IconButton .../>    // columna final opcional
 */
export default function DataTable({
    columns,
    rows = [],
    keyField = 'id',
    loading = false,
    actions,
    emptyTitle = 'Sin registros',
    emptyDescription,
    dense = true,
}) {
    if (loading) return <Loader />;

    if (rows.length === 0) {
        return (
            <Paper variant="outlined">
                <EmptyState title={emptyTitle} description={emptyDescription} />
            </Paper>
        );
    }

    return (
        <TableContainer component={Paper} variant="outlined">
            <Table size={dense ? 'small' : 'medium'}>
                <TableHead>
                    <TableRow>
                        {columns.map((column) => (
                            <TableCell key={column.field} align={column.align} sx={{ width: column.width }}>
                                {column.label}
                            </TableCell>
                        ))}
                        {actions && <TableCell align="right">Acciones</TableCell>}
                    </TableRow>
                </TableHead>
                <TableBody>
                    {rows.map((row) => (
                        <TableRow key={row[keyField]} hover>
                            {columns.map((column) => (
                                <TableCell key={column.field} align={column.align}>
                                    {column.render ? column.render(row) : row[column.field]}
                                </TableCell>
                            ))}
                            {actions && <TableCell align="right">{actions(row)}</TableCell>}
                        </TableRow>
                    ))}
                </TableBody>
            </Table>
        </TableContainer>
    );
}
