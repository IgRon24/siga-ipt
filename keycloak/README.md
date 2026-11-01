# Keycloak en Docker Compose

El contenedor arranca con `start-dev --import-realm` y carga lo que encuentre en
`keycloak/import/`.

**`siga-realm.json` ya está exportado** (realm, roles `ADMIN`/`TEACHER`/`STUDENT`,
el client público `siga-frontend` y los usuarios `admin@siga.cl`,
`teacher@siga.cl`, `student@siga.cl` con sus credenciales). El import se verificó
de punta a punta: login por el puerto 80 del stack y una llamada a `/api/careers`
aceptada por el backend.

Dos cosas que conviene saber de ese archivo:

- **Lleva los hashes de las contraseñas de los tres usuarios.** Son usuarios de
  demostración; si alguna clave se repitiera con algo real, saca el archivo del
  repositorio y cópialo al servidor por `scp`.
- Sus `redirectUris` y `webOrigins` apuntan a `localhost:5173` y `localhost:8080`,
  que es la configuración de desarrollo. **No hace falta tocarlos** para el
  despliegue: la aplicación usa *direct access grant* (sin redirección) y el
  navegador llega a Keycloak por el mismo origen que al frontend, así que no hay
  CORS. Solo habría que agregar la URL de AWS si algún día se cambia al flujo de
  redirección (keycloak-js con PKCE).

Si el directorio `import/` quedara vacío, Keycloak levanta **sin el realm** y
`/auth/realms/siga-realm` responde 404: ningún login funciona.

## Volver a exportar el realm

Hay que hacerlo si cambias usuarios, roles o el client. **No se puede exportar
con el servidor corriendo**: la base H2 de `start-dev` queda bloqueada
(`Database may be already in use`). El camino que funciona es detener el
contenedor, exportar desde una copia de sus datos y volver a levantarlo
(PowerShell, para que Windows no traduzca las rutas del contenedor):

```powershell
$tmp = "$env:TEMP\kcdata"
docker stop keycloak
docker cp keycloak:/opt/keycloak/data $tmp
docker run --rm -v "${tmp}:/opt/keycloak/data" -v "${PWD}\keycloak\import:/export" `
  quay.io/keycloak/keycloak:26.0 export --dir /export --realm siga-realm --users realm_file
docker start keycloak
Move-Item -Force .\keycloak\import\siga-realm-realm.json .\keycloak\import\siga-realm.json
Remove-Item -Recurse -Force $tmp
```

`--users realm_file` mete los usuarios en el mismo archivo. Para que el stack
tome el realm nuevo: `docker compose up -d --force-recreate keycloak`.

Alternativa por la consola: *Realm settings → Action → Partial export*, marcando
grupos, roles y clients. Ese export **no incluye usuarios**, hay que crearlos a
mano después.

## Lo que el realm tiene que tener

- Realm `siga-realm`.
- Roles de realm: `ADMIN`, `TEACHER`, `STUDENT`.
- Client público `siga-frontend` con **Direct access grants** habilitado (es el
  flujo que usa `services/authService.js`).
- Un usuario por rol, con su email cargado: el backend resuelve al estudiante y
  al docente por el claim `email` del token, y ese email tiene que existir
  también como `Student` o `Teacher` en la base de SIGA.

## Por qué el realm vive bajo `/auth`

El compose arranca Keycloak con `KC_HTTP_RELATIVE_PATH=/auth` y el nginx del
borde lo publica en esa misma ruta, sin reescribirla. Así el issuer de los
tokens queda en `<PUBLIC_URL>/auth/realms/siga-realm`, que es exactamente el
valor que el backend valida, y el navegador nunca cruza origenes.

El export del realm no depende de esa ruta: el mismo archivo sirve para tu
Keycloak local en `:8081` y para el del compose.
