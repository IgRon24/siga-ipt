import Button from '@mui/material/Button';
import DialogActions from '@mui/material/DialogActions';

export default function FormActions({
    onCancel,
    submitLabel = 'Guardar',
    cancelLabel = 'Cancelar',
    loading = false,
    disabled = false,
}) {
    return (
        <DialogActions sx={{ px: 0, pb: 0 }}>
            <Button onClick={onCancel} disabled={loading}>
                {cancelLabel}
            </Button>
            <Button type="submit" variant="contained" disabled={loading || disabled}>
                {loading ? 'Guardando...' : submitLabel}
            </Button>
        </DialogActions>
    );
}
