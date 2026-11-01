import { createTheme } from '@mui/material/styles';

/** Tema unico de la aplicacion: colores, tipografia y densidad de las tablas. */
export const theme = createTheme({
    palette: {
        primary: { main: '#1e4e8c' },
        secondary: { main: '#7b1fa2' },
        background: { default: '#fafafa' },
    },
    shape: { borderRadius: 8 },
    typography: {
        fontFamily: ['Segoe UI', 'Roboto', 'Helvetica', 'Arial', 'sans-serif'].join(','),
        h5: { fontWeight: 600 },
        h6: { fontWeight: 600 },
        subtitle1: { fontWeight: 500 },
    },
    components: {
        MuiButton: { defaultProps: { disableElevation: true } },
        MuiTableCell: { styleOverrides: { head: { fontWeight: 600 } } },
    },
});
