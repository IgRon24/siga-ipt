import { Navigate, Route, Routes } from 'react-router-dom';
import AppLayout from '../layouts/AppLayout';
import AuthLayout from '../layouts/AuthLayout';
import ForbiddenPage from '../pages/ForbiddenPage';
import NotFoundPage from '../pages/NotFoundPage';
import LoginPage from '../pages/login/LoginPage';
import ProtectedRoute from './ProtectedRoute';
import { appRoutes } from './routes.config';

/** Las rutas de la aplicacion se arman desde routes.config, no se escriben aqui. */
export default function AppRoutes() {
    return (
        <Routes>
            <Route element={<AuthLayout />}>
                <Route path="/login" element={<LoginPage />} />
            </Route>

            <Route
                element={
                    <ProtectedRoute>
                        <AppLayout />
                    </ProtectedRoute>
                }
            >
                <Route index element={<Navigate to="/dashboard" replace />} />

                {appRoutes.map(({ path, roles, Component, props }) => (
                    <Route
                        key={path}
                        path={path}
                        element={
                            <ProtectedRoute roles={roles}>
                                <Component {...(props ?? {})} />
                            </ProtectedRoute>
                        }
                    />
                ))}

                <Route path="/403" element={<ForbiddenPage />} />
                <Route path="*" element={<NotFoundPage />} />
            </Route>
        </Routes>
    );
}
