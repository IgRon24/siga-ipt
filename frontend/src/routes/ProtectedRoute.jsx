import { Navigate, useLocation } from 'react-router-dom';
import Loader from '../components/ui/Loader';
import { useAuth } from '../hooks/useAuth';

/**
 * Guard de ruta: sin sesion manda a /login (recordando a donde iba) y con un
 * rol insuficiente a /403. Es el espejo de los @PreAuthorize del backend, que
 * de todos modos vuelve a decidir en cada llamada.
 */
export default function ProtectedRoute({ roles, children }) {
    const { isAuthenticated, initializing, hasAnyRole } = useAuth();
    const location = useLocation();

    // Mientras se rehidrata la sesion no se puede decidir todavia
    if (initializing) {
        return <Loader label="Verificando sesion..." minHeight="60vh" />;
    }

    if (!isAuthenticated) {
        return <Navigate to="/login" replace state={{ from: location }} />;
    }

    if (roles && !hasAnyRole(roles)) {
        return <Navigate to="/403" replace />;
    }

    return children;
}
