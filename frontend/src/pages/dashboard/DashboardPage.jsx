import { Link as RouterLink } from 'react-router-dom';
import Card from '@mui/material/Card';
import CardActionArea from '@mui/material/CardActionArea';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import Grid from '@mui/material/Grid';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import PageHeader from '../../components/ui/PageHeader';
import { useAuth } from '../../hooks/useAuth';
import { appRoutes } from '../../routes/routes.config';

/**
 * Inicio comun a los tres roles: las tarjetas se arman del mismo
 * routes.config que alimenta el menu y el guard, asi nunca ofrece algo que el
 * backend vaya a rechazar con 403.
 */
export default function DashboardPage() {
    const { fullName, username, roles, hasAnyRole } = useAuth();

    const shortcuts = appRoutes.filter(
        (route) => route.path !== '/dashboard' && route.label && hasAnyRole(route.roles),
    );

    return (
        <>
            <PageHeader
                title={`hola mundo, ${fullName || username}`}
                subtitle="Accesos disponibles para tu rol"
                action={
                    <Stack direction="row" spacing={1}>
                        {roles.map((role) => (
                            <Chip key={role} label={role} color="primary" size="small" />
                        ))}
                    </Stack>
                }
            />

            <Grid container spacing={2}>
                {shortcuts.map((route) => {
                    const Icon = route.icon;
                    return (
                        <Grid key={route.path} size={{ xs: 12, sm: 6, md: 4 }}>
                            <Card variant="outlined" sx={{ height: '100%' }}>
                                <CardActionArea component={RouterLink} to={route.path} sx={{ height: '100%' }}>
                                    <CardContent>
                                        <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
                                            {Icon && <Icon color="primary" />}
                                            <Typography variant="subtitle1">{route.label}</Typography>
                                        </Stack>
                                        {route.description && (
                                            <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                                                {route.description}
                                            </Typography>
                                        )}
                                    </CardContent>
                                </CardActionArea>
                            </Card>
                        </Grid>
                    );
                })}
            </Grid>
        </>
    );
}
