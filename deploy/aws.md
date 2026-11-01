# Despliegue de demostración en AWS (EC2)

El sistema completo corre con `docker compose` en una sola instancia. La
instancia **no compila nada**: baja las imágenes de Docker Hub.

## 0. Antes de empezar

Las imágenes tienen que estar publicadas (desde tu máquina):

```bash
docker login -u ronaldgarrido24
docker compose build
docker compose push
```

Y el realm de Keycloak tiene que estar en `keycloak/import/siga-realm.json`
(ver `keycloak/README.md`). Si ese archivo no viaja al servidor, Keycloak
levanta sin el realm y nadie puede entrar.

## 1. La instancia

| | |
| --- | --- |
| Tipo | **t3.medium** (2 vCPU, 4 GB) |
| AMI | Ubuntu Server 24.04 LTS o Amazon Linux 2023, **x86_64** |
| Disco | 20 GB gp3 |

Dos cosas que importan:

- **x86_64, no ARM.** Las imágenes se construyeron en tu PC (amd64) y no corren
  en una `t4g`. Si quieres ARM, hay que construirlas con
  `docker buildx build --platform linux/arm64`.
- **4 GB de RAM.** Consumo medido del stack completo, con los límites del
  compose puestos:

  | Servicio | Uso real | Límite |
  | --- | --- | --- |
  | backend ×3 | ~285 MB cada uno | 640 MB |
  | keycloak | ~517 MB | 768 MB |
  | postgres | ~85 MB | — |
  | nginx + frontend | ~14 MB | — |
  | **total** | **~1.5 GB** | ~3 GB |

  Con 4 GB (`t3.medium`) sobra holgura. Una `t3.small` de 2 GB alcanza justo,
  dejando poco para el sistema operativo; en una `t3.micro` de 1 GB el stack
  muere. Si tuvieras que usar 2 GB, levanta solo el compose principal, que trae
  dos réplicas (pero la rúbrica pide tres, así que para la evaluación conviene
  la instancia que las aguante).

**Security group:**

| Puerto | Origen | Para qué |
| --- | --- | --- |
| 22 | tu IP | SSH |
| 80 | 0.0.0.0/0 | la aplicación |

Nada más: la base de datos y los backends no publican puertos.

## 2. Docker en la instancia

Ubuntu 24.04:

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl git
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
  https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
sudo usermod -aG docker $USER && newgrp docker
```

Amazon Linux 2023:

```bash
sudo dnf install -y docker git
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user && newgrp docker
sudo dnf install -y docker-compose-plugin
```

## 3. El proyecto

```bash
git clone https://github.com/IgRon24/siga-ipt.git
cd siga-ipt
cp .env.example .env
nano .env
```

En `.env`, lo que hay que completar:

```ini
POSTGRES_PASSWORD=<una clave larga>
KEYCLOAK_ADMIN_PASSWORD=<una clave larga, no "admin">
DOCKERHUB_USER=ronaldgarrido24

# La URL con la que vas a abrir el sistema en el navegador
PUBLIC_URL=http://ec2-00-00-00-00.compute-1.amazonaws.com
```

`PUBLIC_URL` es la variable crítica: de ella salen el `issuer` de los tokens de
Keycloak y el valor que el backend valida, así que ambos cambian juntos y nunca
se desalinean. Puede ser el DNS público o la IP elástica, pero **tiene que ser
la misma URL que uses en el navegador**, y sin barra final.

El frontend no necesita reconstruirse para la nube: se compiló con rutas
relativas (`/api`, `/auth`), así que la misma imagen sirve en localhost y en
AWS.

## 4. Levantar

En la nube se levantan las **tres** réplicas que pide la rúbrica, así que van
los dos archivos de compose (el principal trae dos; el segundo agrega la
tercera y le avisa a nginx):

```bash
export COMPOSE_FILE=docker-compose.yml:docker-compose.three.yml
docker compose pull            # baja las imágenes de Docker Hub
docker compose up -d --no-build
docker compose ps              # las tres réplicas deben quedar (healthy)
```

`COMPOSE_FILE` queda en el entorno de la sesión: si abres otra terminal,
expórtalo de nuevo o pasa los dos `-f` a mano.  Sin él, `docker compose` usa
solo el principal y la tercera réplica queda fuera.

Verificación rápida:

```bash
curl -s localhost/actuator/health              # {"status":"UP"}
curl -s -o /dev/null -w '%{http_code}\n' localhost/api/careers   # 401 sin token
curl -s localhost/auth/realms/siga-realm | head -c 120           # el realm existe
```

Y en el navegador: `http://<PUBLIC_URL>` para la aplicación,
`http://<PUBLIC_URL>/auth/admin` para la consola de Keycloak.

## 4.1 Cargar los datos de la demo

La base arranca vacía, así que el sistema se ve funcionando pero sin una sola
carrera. El seed la llena por la API:

```bash
export SIGA_ADMIN_PASS=... SIGA_TEACHER_PASS=... SIGA_STUDENT_PASS=...
SIGA_URL=http://localhost ./deploy/seed.sh      # desde la propia instancia
```

Deja las 3 carreras del Apéndice A con sus 72 asignaturas y prerrequisitos,
docentes, estudiantes, el período **2025-2 cerrado con historial** y **2026-1
abierto con oferta lista para inscribir**. Es idempotente.

Para comprobar el balanceo en vivo:

```bash
docker compose logs -f nginx       # cada línea muestra a qué réplica fue
docker compose stop backend-2      # el sistema sigue respondiendo
docker compose start backend-2
```

## 5. Seguridad mínima de la demo

El sistema queda publicado en HTTP sin TLS, así que:

- **Keycloak rechaza el login desde internet por diseño.** El realm trae
  `"sslRequired": "external"`, el valor por defecto: HTTPS obligatorio salvo
  para localhost. De ahí el `HTTPS required` del navegador mientras `seed.sh`
  por `localhost` funciona sin chistar. Hay dos salidas y conviene tener claro
  el costo de cada una:

  1. **Poner TLS de verdad** (dominio + Let's Encrypt sobre el mismo nginx). No
     se toca el realm y las credenciales viajan cifradas. Es lo correcto para
     cualquier cosa que no sea una demo.
  2. **Bajar la política a `none`**, que es lo que permite demostrar el sistema
     sobre una IP pública en HTTP. Implica que **las contraseñas viajan en
     claro**, igual que ya lo hace todo el resto del tráfico de esta demo. Es
     una decisión conciente de demostración, no un descuido, y no debe quedar
     así en nada real.

  Si eliges la segunda, se cambia en los dos realms (`siga-realm` para la
  aplicación y `master` para la consola) desde dentro del contenedor, donde
  localhost está exento.


- **Cambia `KEYCLOAK_ADMIN_PASSWORD`.** La consola de administración de Keycloak
  queda accesible desde internet en `/auth/admin`; con `admin`/`admin` cualquiera
  toma el realm y, con él, todos los roles del sistema.
- Si quieres cerrarla salvo para ti, agrega en `nginx/nginx.conf`:

  ```nginx
  location /auth/admin {
      allow <tu.ip.publica>;
      deny all;
      proxy_pass http://keycloak:8080;
      proxy_set_header Host $host;
      proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
      proxy_set_header X-Forwarded-Proto $scheme;
  }
  ```

- Apaga la instancia cuando no la estés mostrando: `docker compose stop` y
  *Instance state → Stop*. Los volúmenes (base de datos y Keycloak) sobreviven.

Para TLS de verdad harían falta un dominio y Let's Encrypt (certbot sobre el
mismo nginx). Fuera del alcance de una demo, pero es el siguiente paso natural.

## 6. Actualizar la demo

### Automático, con cada push a `main`

El job `deploy` de `.github/workflows/ci.yml` entra por SSH a la instancia y
corre `deploy/update.sh`. Va al final de la cadena y **depende de `images`**:
si las pruebas fallan no se publica nada, y si no se publica nada no se
despliega nada. Un tag de versión publica imágenes pero no toca la demo.

Necesita tres secretos en el repositorio (`Settings → Secrets and variables →
Actions`): `EC2_HOST`, `EC2_USER` y `EC2_SSH_KEY` (el contenido completo del
`.pem`, con sus líneas `BEGIN`/`END`).

Y necesita que el **puerto 22 del security group acepte** a los runners de
GitHub, que usan IPs dinámicas de Microsoft: en la práctica, `0.0.0.0/0`. El
acceso sigue siendo solo por llave (Amazon Linux no permite password por SSH),
pero conviene tenerlo claro y volver a cerrar el 22 a tu IP cuando termine la
evaluación — el despliegue automático deja de funcionar y se actualiza a mano,
que es un comando.

### A mano, en cualquier momento

```bash
cd ~/siga-ipt && git pull && bash deploy/update.sh
```

`deploy/update.sh` es el mismo script que usa el workflow. Baja las imágenes y
reemplaza **réplica por réplica**, esperando que cada una quede `healthy` antes
de tocar la siguiente, así nginx siempre tiene una sana a la que mandar tráfico
y la demo no se cae durante el despliegue. Al final verifica
`/actuator/health`. No toca volúmenes: los datos de la demo sobreviven a
cualquier actualización.

Para desplegar una versión concreta en lugar de `latest`, desde tu máquina:

```bash
TAG=1.1.0 docker compose build
TAG=1.1.0 docker compose push
```

y en la instancia, `sed -i 's/^TAG=.*/TAG=1.1.0/' .env` antes de correr
`update.sh`.

## Si algo falla

| Síntoma | Causa |
| --- | --- |
| Keycloak responde **`HTTPS required`** al entrar desde el navegador, pero el seed por `localhost` funcionó | el realm viaja con `"sslRequired": "external"`, que es el valor por defecto de Keycloak: exige HTTPS salvo para localhost. Por eso solo falla desde fuera. Ver la sección 5 |
| Login dice "no se pudo iniciar sesión" y `/auth/realms/siga-realm` da 404 | falta `keycloak/import/siga-realm.json` |
| Todas las llamadas a la API dan 401 con token válido | `PUBLIC_URL` no coincide con la URL del navegador: el `issuer` del token no es el que valida el backend |
| `exec format error` al arrancar un contenedor | instancia ARM con imágenes amd64 |
| Contenedores que se reinician solos | memoria: `docker stats` y `free -h`; subir el tipo de instancia |
| nginx muere al reiniciar la instancia | arrancó antes que los backends; `restart: unless-stopped` lo recupera, o `docker compose restart nginx` |
