import { createContext, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { configureAuth } from '../api/client';
import * as authService from '../services/authService';

/**
 * Dueno de la sesion: token, email y roles del JWT.
 *
 * Es la unica fuente de verdad del token. El cliente HTTP no lo guarda: lo pide
 * con el callback que se registra aqui (configureAuth), y cuando el backend
 * responde 401 el cierre de sesion ocurre tambien en este unico lugar.
 */
export const AuthContext = createContext(null);

export function AuthProvider({ children }) {
    const [session, setSession] = useState(null);
    const [initializing, setInitializing] = useState(true);

    // El interceptor corre fuera de React: necesita una referencia viva
    const sessionRef = useRef(null);

    const applySession = useCallback((next) => {
        sessionRef.current = next;
        setSession(next);
        if (next) {
            authService.saveSession(next);
        } else {
            authService.clearSession();
        }
    }, []);

    useEffect(() => {
        configureAuth({
            getToken: () => sessionRef.current?.accessToken ?? null,
            onUnauthorized: () => applySession(null),
        });
    }, [applySession]);

    // Rehidratacion: si el token guardado expiro, se intenta refrescar una vez
    useEffect(() => {
        const stored = authService.loadSession();
        if (!stored) {
            setInitializing(false);
            return;
        }
        if (!authService.isExpired(stored)) {
            applySession(stored);
            setInitializing(false);
            return;
        }
        authService
            .refresh(stored.refreshToken)
            .then(applySession)
            .catch(() => applySession(null))
            .finally(() => setInitializing(false));
    }, [applySession]);

    // Refresco proactivo un minuto antes de que expire el access token
    useEffect(() => {
        if (!session?.refreshToken) return undefined;
        const delay = Math.max(session.expiresAt - Date.now() - 60_000, 5_000);
        const timer = setTimeout(() => {
            authService
                .refresh(session.refreshToken)
                .then(applySession)
                .catch(() => applySession(null));
        }, delay);
        return () => clearTimeout(timer);
    }, [session, applySession]);

    const signIn = useCallback(
        async (username, password) => {
            const next = await authService.login(username, password);
            applySession(next);
            return next;
        },
        [applySession],
    );

    const signOut = useCallback(async () => {
        const current = sessionRef.current;
        applySession(null);
        await authService.logout(current);
    }, [applySession]);

    const value = useMemo(() => {
        const roles = session?.roles ?? [];
        return {
            session,
            initializing,
            isAuthenticated: Boolean(session),
            email: session?.email ?? '',
            username: session?.username ?? '',
            fullName: session?.fullName ?? '',
            roles,
            hasRole: (role) => roles.includes(role),
            hasAnyRole: (allowed) => !allowed?.length || allowed.some((role) => roles.includes(role)),
            signIn,
            signOut,
        };
    }, [session, initializing, signIn, signOut]);

    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
