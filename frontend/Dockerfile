# Imagen del frontend: Vite compila a estaticos y nginx los sirve.
#
# Vite incrusta las variables VITE_* en tiempo de compilacion, no de ejecucion:
# por eso llegan como build args. Por defecto son rutas relativas, porque el
# nginx del borde (../nginx) publica el frontend, /api y /auth en un solo
# origen, igual que el proxy del dev server.

# ---------- build ----------
FROM node:22-alpine AS build
WORKDIR /build

ARG VITE_API_URL=/api
ARG VITE_KC_URL=/auth
ARG VITE_KC_REALM=siga-realm
ARG VITE_KC_CLIENT_ID=siga-frontend
ARG VITE_KC_CLIENT_SECRET=

ENV VITE_API_URL=$VITE_API_URL \
    VITE_KC_URL=$VITE_KC_URL \
    VITE_KC_REALM=$VITE_KC_REALM \
    VITE_KC_CLIENT_ID=$VITE_KC_CLIENT_ID \
    VITE_KC_CLIENT_SECRET=$VITE_KC_CLIENT_SECRET

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---------- runtime ----------
FROM nginx:1.27-alpine AS runtime

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html

EXPOSE 80
