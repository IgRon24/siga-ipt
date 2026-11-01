import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogTitle from '@mui/material/DialogTitle';
import ErrorAlert from './ErrorAlert';

/**
 * Confirmacion para acciones destructivas. El error se muestra dentro del
 * dialogo porque muchos borrados responden 409 (referencias academicas) y el
 * mensaje del backend es justamente la explicacion que necesita el usuario.
 */
export default function ConfirmDialog({
    open,
    title = 'Confirmar',
    message,
    confirmLabel = 'Confirmar',
    cancelLabel = 'Cancelar',
    confirmColor = 'error',
    loading = false,
    error,
    onConfirm,
    onClose,
}) {
    return (
        <Dialog open={open} onClose={loading ? undefined : onClose} fullWidth maxWidth="xs">
            <DialogTitle>{title}</DialogTitle>
            <DialogContent>
                <ErrorAlert error={error} />
                <DialogContentText>{message}</DialogContentText>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose} disabled={loading}>
                    {cancelLabel}
                </Button>
                <Button onClick={onConfirm} color={confirmColor} variant="contained" disabled={loading}>
                    {confirmLabel}
                </Button>
            </DialogActions>
        </Dialog>
    );
}
