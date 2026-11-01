import { useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import LogoutIcon from '@mui/icons-material/Logout';
import MenuIcon from '@mui/icons-material/Menu';
import AppBar from '@mui/material/AppBar';
import Avatar from '@mui/material/Avatar';
import Box from '@mui/material/Box';
import Container from '@mui/material/Container';
import Divider from '@mui/material/Divider';
import Drawer from '@mui/material/Drawer';
import IconButton from '@mui/material/IconButton';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Stack from '@mui/material/Stack';
import Toolbar from '@mui/material/Toolbar';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { useAuth } from '../hooks/useAuth';
import { routesForRoles } from '../routes/routes.config';
import { initialsOf } from '../utils/formatters';

const DRAWER_WIDTH = 240;

/**
 * Marco de la aplicacion autenticada. El menu se arma con routesForRoles, el
 * mismo config que usa el guard: un rol nunca puede quedar desincronizado
 * entre lo que se ofrece y lo que se permite.
 */
export default function AppLayout() {
    const { roles, email, fullName, username, signOut } = useAuth();
    const navigate = useNavigate();
    const [mobileOpen, setMobileOpen] = useState(false);

    const menu = routesForRoles(roles);

    const handleSignOut = async () => {
        await signOut();
        navigate('/login', { replace: true });
    };

    const navigation = (
        <>
            <Toolbar sx={{ gap: 1 }}>
                <Typography variant="h6" noWrap>
                    SIGA
                </Typography>
            </Toolbar>
            <Divider />
            <List>
                {menu.map((route) => {
                    const Icon = route.icon;
                    return (
                        <ListItemButton
                            key={route.path}
                            component={NavLink}
                            to={route.path}
                            onClick={() => setMobileOpen(false)}
                            sx={{
                                '&.active': {
                                    bgcolor: 'action.selected',
                                    '& .MuiListItemIcon-root': { color: 'primary.main' },
                                },
                            }}
                        >
                            <ListItemIcon>{Icon && <Icon />}</ListItemIcon>
                            <ListItemText primary={route.label} />
                        </ListItemButton>
                    );
                })}
            </List>
        </>
    );

    return (
        <Box sx={{ display: 'flex', minHeight: '100vh', bgcolor: 'grey.50' }}>
            <AppBar
                position="fixed"
                color="inherit"
                elevation={0}
                sx={{
                    borderBottom: 1,
                    borderColor: 'divider',
                    width: { md: `calc(100% - ${DRAWER_WIDTH}px)` },
                    ml: { md: `${DRAWER_WIDTH}px` },
                }}
            >
                <Toolbar>
                    <IconButton
                        edge="start"
                        onClick={() => setMobileOpen((open) => !open)}
                        sx={{ mr: 2, display: { md: 'none' } }}
                    >
                        <MenuIcon />
                    </IconButton>

                    <Box sx={{ flexGrow: 1 }} />

                    <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
                        <Box sx={{ textAlign: 'right', display: { xs: 'none', sm: 'block' } }}>
                            <Typography variant="body2">{fullName || username}</Typography>
                            <Typography variant="caption" color="text.secondary">
                                {email} · {roles.join(', ')}
                            </Typography>
                        </Box>
                        <Avatar sx={{ width: 36, height: 36 }}>{initialsOf(fullName || username)}</Avatar>
                        <Tooltip title="Cerrar sesion">
                            <IconButton onClick={handleSignOut}>
                                <LogoutIcon />
                            </IconButton>
                        </Tooltip>
                    </Stack>
                </Toolbar>
            </AppBar>

            <Drawer
                variant="temporary"
                open={mobileOpen}
                onClose={() => setMobileOpen(false)}
                sx={{
                    display: { xs: 'block', md: 'none' },
                    '& .MuiDrawer-paper': { width: DRAWER_WIDTH, boxSizing: 'border-box' },
                }}
            >
                {navigation}
            </Drawer>

            <Drawer
                variant="permanent"
                sx={{
                    display: { xs: 'none', md: 'block' },
                    width: DRAWER_WIDTH,
                    flexShrink: 0,
                    '& .MuiDrawer-paper': { width: DRAWER_WIDTH, boxSizing: 'border-box' },
                }}
                open
            >
                {navigation}
            </Drawer>

            <Box component="main" sx={{ flexGrow: 1, width: { md: `calc(100% - ${DRAWER_WIDTH}px)` } }}>
                <Toolbar />
                <Container maxWidth="xl" sx={{ py: 3 }}>
                    <Outlet />
                </Container>
            </Box>
        </Box>
    );
}
