import MenuItem from '@mui/material/MenuItem';
import MuiTextField from '@mui/material/TextField';

/** Select con el mismo contrato que TextField: options = [{value, label}]. */
export default function SelectField({ error, helperText, options = [], placeholder, ...props }) {
    return (
        <MuiTextField
            select
            fullWidth
            size="small"
            margin="dense"
            error={Boolean(error)}
            helperText={error ?? helperText}
            {...props}
        >
            {placeholder && (
                <MenuItem value="">
                    <em>{placeholder}</em>
                </MenuItem>
            )}
            {options.map((option) => (
                <MenuItem key={option.value} value={option.value}>
                    {option.label}
                </MenuItem>
            ))}
        </MuiTextField>
    );
}
