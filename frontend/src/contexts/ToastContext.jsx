import { createContext, useCallback, useMemo, useState } from 'react';
import Alert from '@mui/material/Alert';
import Snackbar from '@mui/material/Snackbar';

/** Avisos cortos de resultado. Los errores de formulario no van aqui: van al campo. */
export const ToastContext = createContext(null);

export function ToastProvider({ children }) {
    const [toast, setToast] = useState(null);

    const show = useCallback((message, severity = 'info') => {
        setToast({ message, severity, key: Date.now() });
    }, []);

    const value = useMemo(
        () => ({
            show,
            showSuccess: (message) => show(message, 'success'),
            showError: (message) => show(message, 'error'),
            showInfo: (message) => show(message, 'info'),
            showWarning: (message) => show(message, 'warning'),
        }),
        [show],
    );

    return (
        <ToastContext.Provider value={value}>
            {children}
            <Snackbar
                key={toast?.key}
                open={Boolean(toast)}
                autoHideDuration={5000}
                onClose={() => setToast(null)}
                anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
            >
                <Alert severity={toast?.severity ?? 'info'} variant="filled" onClose={() => setToast(null)}>
                    {toast?.message}
                </Alert>
            </Snackbar>
        </ToastContext.Provider>
    );
}
