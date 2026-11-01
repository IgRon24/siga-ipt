import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

/**
 * En desarrollo el dev server hace de proxy hacia el backend y hacia Keycloak,
 * asi el navegador ve un solo origen (http://localhost:5173) y no hay CORS que
 * configurar: ni en Spring Security ni en los Web Origins del client. En
 * produccion ese papel lo cumple Nginx, con el mismo esquema de rutas.
 *
 * Por eso .env usa rutas relativas: VITE_API_URL=/api y VITE_KC_URL=/auth.
 */
export default defineConfig({
    plugins: [react()],
    server: {
        port: 5173,
        proxy: {
            '/api': {
                target: 'http://localhost:8080',
                changeOrigin: true,
            },
            '/auth': {
                target: 'http://localhost:8081',
                changeOrigin: true,
                rewrite: (path) => path.replace(/^\/auth/, ''),
            },
        },
    },
});
