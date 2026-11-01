import MuiTextField from '@mui/material/TextField';

/**
 * Campo de texto atado a useForm: recibe error como string (venga de una
 * validacion local o del mapa de fieldErrors del backend) y lo traduce a la
 * forma que espera MUI.
 */
export default function TextField({ error, helperText, ...props }) {
    return (
        <MuiTextField
            fullWidth
            size="small"
            margin="dense"
            error={Boolean(error)}
            helperText={error ?? helperText}
            {...props}
        />
    );
}
