import { useState } from 'react';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import Stack from '@mui/material/Stack';
import Tooltip from '@mui/material/Tooltip';
import StatusChip from '../../components/domain/StatusChip';
import ConfirmDialog from '../../components/ui/ConfirmDialog';
import DataTable from '../../components/ui/DataTable';
import ErrorAlert from '../../components/ui/ErrorAlert';
import PageHeader from '../../components/ui/PageHeader';
import { useApiResource } from '../../hooks/useApiResource';
import { useAuth } from '../../hooks/useAuth';
import { useToast } from '../../hooks/useToast';
import { careerService } from '../../services/careerService';
import CareerForm from './CareerForm';

/**
 * Gestion de carreras: la pantalla de referencia del CRUD.
 *
 * Toda la estructura esta aqui y se repite en las demas: useApiResource para
 * la coleccion, DataTable para listar, un Dialog con el formulario para
 * crear/editar y ConfirmDialog para el borrado, que en este sistema suele
 * responder 409 cuando la carrera tiene estudiantes o planes.
 */
export default function CareersPage() {
    const careers = useApiResource(careerService);
    const { hasRole } = useAuth();
    const { showSuccess } = useToast();
    const isAdmin = hasRole('ADMIN');

    const [editing, setEditing] = useState(null); // null = cerrado, {} = nueva
    const [deleting, setDeleting] = useState(null);
    const [deleteError, setDeleteError] = useState(null);
    const [deleteLoading, setDeleteLoading] = useState(false);

    const columns = [
        { field: 'code', label: 'Codigo', width: 110 },
        { field: 'name', label: 'Nombre' },
        { field: 'description', label: 'Descripcion' },
        {
            field: 'status',
            label: 'Estado',
            width: 130,
            render: (row) => <StatusChip kind="career" value={row.status} />,
        },
    ];

    const handleSubmit = async (values) => {
        const result = editing?.id
            ? await careers.update(editing.id, values)
            : await careers.create(values);

        if (!result.ok) {
            // El formulario lo muestra en su propio contexto: no se duplica en la pagina
            careers.setError(null);
            throw result.error;
        }
        showSuccess(editing?.id ? 'Carrera actualizada' : 'Carrera creada');
        return result.data;
    };

    const handleDelete = async () => {
        setDeleteLoading(true);
        setDeleteError(null);
        const { ok, error } = await careers.remove(deleting.id);
        setDeleteLoading(false);

        if (ok) {
            setDeleting(null);
            showSuccess('Carrera eliminada');
        } else {
            setDeleteError(error);
        }
    };

    return (
        <>
            <PageHeader
                title="Carreras"
                subtitle="El codigo es unico en el sistema y cada carrera tiene a lo mas un plan VIGENTE"
                action={
                    isAdmin && (
                        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEditing({})}>
                            Nueva carrera
                        </Button>
                    )
                }
            />

            <ErrorAlert error={careers.error} onRetry={careers.reload} onClose={() => careers.setError(null)} />

            <DataTable
                columns={columns}
                rows={careers.items}
                loading={careers.loading}
                emptyTitle="No hay carreras registradas"
                emptyDescription={isAdmin ? 'Crea la primera con el boton de arriba.' : undefined}
                actions={
                    isAdmin
                        ? (row) => (
                              <Stack direction="row" spacing={0.5} sx={{ justifyContent: 'flex-end' }}>
                                  <Tooltip title="Editar">
                                      <IconButton size="small" onClick={() => setEditing(row)}>
                                          <EditIcon fontSize="small" />
                                      </IconButton>
                                  </Tooltip>
                                  <Tooltip title="Eliminar">
                                      <IconButton size="small" onClick={() => setDeleting(row)}>
                                          <DeleteIcon fontSize="small" />
                                      </IconButton>
                                  </Tooltip>
                              </Stack>
                          )
                        : undefined
                }
            />

            <CareerForm
                open={Boolean(editing)}
                career={editing?.id ? editing : null}
                onSubmit={handleSubmit}
                onClose={() => setEditing(null)}
            />

            <ConfirmDialog
                open={Boolean(deleting)}
                title="Eliminar carrera"
                message={`Se eliminara la carrera ${deleting?.code} - ${deleting?.name}. No se puede eliminar si tiene planes o estudiantes.`}
                confirmLabel="Eliminar"
                loading={deleteLoading}
                error={deleteError}
                onConfirm={handleDelete}
                onClose={() => {
                    setDeleting(null);
                    setDeleteError(null);
                }}
            />
        </>
    );
}
