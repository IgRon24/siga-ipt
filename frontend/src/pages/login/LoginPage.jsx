import { useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';
import ErrorAlert from '../../components/ui/ErrorAlert';
import TextField from '../../components/form/TextField';
import { useApiCall } from '../../hooks/useApiCall';
import { useAuth } from '../../hooks/useAuth';

/**
 * Login contra Keycloak. El rol no se elige: viene en el claim realm_access
 * del token y define que ve el usuario de aqui en adelante.
 */
export default function LoginPage() {
    const { signIn, isAuthenticated } = useAuth();
    const navigate = useNavigate();
    const location = useLocation();
    const from = location.state?.from?.pathname ?? '/dashboard';

    const [values, setValues] = useState({ username: '', password: '' });
    const { call, loading, error } = useApiCall(signIn);

    if (isAuthenticated) {
        return <Navigate to={from} replace />;
    }

    const handleChange = (event) => {
        const { name, value } = event.target;
        setValues((current) => ({ ...current, [name]: value }));
    };

    const handleSubmit = async (event) => {
        event.preventDefault();
        const { ok } = await call(values.username, values.password);
        if (ok) navigate(from, { replace: true });
    };

    return (
        <Box component="form" onSubmit={handleSubmit} noValidate>
            <Typography variant="h5" component="h1" gutterBottom>
                SIGA
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
                Sistema Integrado de Gestion Academica
            </Typography>

            <ErrorAlert error={error} />

            <TextField
                label="Usuario"
                name="username"
                value={values.username}
                onChange={handleChange}
                autoComplete="username"
                autoFocus
                required
            />
            <TextField
                label="Contrasena"
                name="password"
                type="password"
                value={values.password}
                onChange={handleChange}
                autoComplete="current-password"
                required
            />

            <Button type="submit" variant="contained" fullWidth size="large" sx={{ mt: 3 }} disabled={loading}>
                {loading ? 'Ingresando...' : 'Ingresar'}
            </Button>
        </Box>
    );
}
