# SIGA IPT - Prueba manual de Epica 4: Gestion de docentes
#
# Uso desde cmd (login automatico contra Keycloak):
#   set KC_CLIENT_ID=tu-client-id
#   set KC_ADMIN_USER=tu-usuario-admin
#   set KC_ADMIN_PASS=tu-password
#   set KC_STUDENT_USER=tu-usuario-student
#   set KC_STUDENT_PASS=tu-password
#   powershell -ExecutionPolicy Bypass -File http\teachers.ps1
#
# Uso desde cmd (tokens a mano):
#   set ADMIN_TOKEN=eyJ...
#   set STUDENT_TOKEN=eyJ...
#   powershell -ExecutionPolicy Bypass -File http\teachers.ps1

param(
    [string]$BaseUrl      = "http://localhost:8080",
    [string]$AdminToken   = $env:ADMIN_TOKEN,
    [string]$StudentToken = $env:STUDENT_TOKEN,

    [string]$KeycloakUrl  = "http://localhost:8081",
    [string]$Realm        = "siga-realm",
    [string]$ClientId     = $env:KC_CLIENT_ID,
    [string]$ClientSecret = $env:KC_CLIENT_SECRET,
    [string]$AdminUser    = $env:KC_ADMIN_USER,
    [string]$AdminPass    = $env:KC_ADMIN_PASS,
    [string]$StudentUser  = $env:KC_STUDENT_USER,
    [string]$StudentPass  = $env:KC_STUDENT_PASS
)

$ErrorActionPreference = "Stop"
$script:pass = 0
$script:fail = 0

# RUN y email unicos por corrida: el RUN se arma con la hora (8 digitos + verificador).
$stamp = Get-Date -Format "HHmmss"
$run1  = ("1" + $stamp + "1").Substring(0, 8) + "-9"
$run2  = ("2" + $stamp + "2").Substring(0, 8) + "-7"
$run3  = ("3" + $stamp + "3").Substring(0, 8) + "-5"
$mail1 = "ana.perez.$stamp@ipt.cl"
$mail2 = "luis.soto.$stamp@ipt.cl"

function Get-KeycloakToken {
    param([string]$User, [string]$Pass, [string]$Label)

    $form = @{
        grant_type = "password"
        client_id  = $ClientId
        username   = $User
        password   = $Pass
    }
    if ($ClientSecret) { $form["client_secret"] = $ClientSecret }

    Write-Host "Obteniendo token de $Label desde Keycloak..."
    try {
        $response = Invoke-RestMethod -Uri "$KeycloakUrl/realms/$Realm/protocol/openid-connect/token" -Method Post -Body $form
        return $response.access_token
    } catch {
        Write-Host "  No se pudo obtener el token de $Label : $($_.Exception.Message)" -ForegroundColor Red
        return $null
    }
}

function Invoke-Req {
    param(
        [string]$Desc,
        [int]$Expected,
        [string]$Method,
        [string]$Path,
        [string]$Token,
        $Body
    )

    $headers = @{}
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }

    $reqArgs = @{
        Uri             = "$BaseUrl$Path"
        Method          = $Method
        Headers         = $headers
        UseBasicParsing = $true
    }
    if ($null -ne $Body) {
        $reqArgs["Body"]        = ($Body | ConvertTo-Json -Compress)
        $reqArgs["ContentType"] = "application/json"
    }

    $status  = 0
    $content = ""
    try {
        $response = Invoke-WebRequest @reqArgs
        $status   = [int]$response.StatusCode
        $content  = $response.Content
    } catch {
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode.value__
            try {
                $reader  = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
                $content = $reader.ReadToEnd()
                $reader.Close()
            } catch { $content = "" }
        } else {
            Write-Host "  [ERROR] $Desc : $($_.Exception.Message)" -ForegroundColor Red
            $script:fail++
            return $null
        }
    }

    if ($status -eq $Expected) {
        Write-Host ("  [OK]    {0,-3}  {1}" -f $status, $Desc) -ForegroundColor Green
        $script:pass++
    } else {
        Write-Host ("  [FALLA] {0,-3}  {1}  (se esperaba {2})" -f $status, $Desc, $Expected) -ForegroundColor Red
        Write-Host "          $content" -ForegroundColor DarkGray
        $script:fail++
    }

    if ($content) {
        try { return ($content | ConvertFrom-Json) } catch { return $null }
    }
    return $null
}

function New-TeacherBody {
    param([string]$Run, [string]$Name, [string]$Email, [string]$Title, [string]$Degree)
    return @{
        run               = $Run
        fullName          = $Name
        email             = $Email
        professionalTitle = $Title
        academicDegree    = $Degree
    }
}

# ---------------------------------------------------------------
# Tokens
# ---------------------------------------------------------------

if (-not $AdminToken -and $AdminUser) {
    $AdminToken = Get-KeycloakToken -User $AdminUser -Pass $AdminPass -Label "ADMIN"
}
if (-not $StudentToken -and $StudentUser) {
    $StudentToken = Get-KeycloakToken -User $StudentUser -Pass $StudentPass -Label "STUDENT"
}

if (-not $AdminToken) {
    Write-Host ""
    Write-Host "ERROR: falta el token de ADMIN." -ForegroundColor Red
    Write-Host "  Opcion 1:  set ADMIN_TOKEN=eyJ..."
    Write-Host "  Opcion 2:  set KC_CLIENT_ID / KC_ADMIN_USER / KC_ADMIN_PASS  (login automatico)"
    Write-Host ""
    exit 1
}
if (-not $StudentToken) {
    Write-Host "AVISO: sin token de STUDENT, las pruebas de rol se van a saltar." -ForegroundColor Yellow
}

# ---------------------------------------------------------------
# ALTA Y ESTADO INICIAL
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== ALTA Y ESTADO INICIAL =====================================" -ForegroundColor Cyan

$teacher = Invoke-Req "Crear docente $run1" 201 POST "/api/teachers" $AdminToken `
    (New-TeacherBody $run1 "Ana Perez" $mail1 "Ingeniera en Informatica" "Magister en Ciencias de la Computacion")
if (-not $teacher) { Write-Host "No se pudo crear el docente, se aborta." -ForegroundColor Red; exit 1 }
$teacherId = $teacher.id

if ($teacher.status -eq "ACTIVO") {
    Write-Host "  [OK]         El docente quedo en estado ACTIVO automaticamente" -ForegroundColor Green
    $script:pass++
} else {
    Write-Host "  [FALLA]      Se esperaba status ACTIVO y llego '$($teacher.status)'" -ForegroundColor Red
    $script:fail++
}

$teacher2 = Invoke-Req "Crear un segundo docente" 201 POST "/api/teachers" $AdminToken `
    (New-TeacherBody $run2 "Luis Soto" $mail2 "Ingeniero Civil Informatico" "Magister en Redes")
$teacher2Id = $teacher2.id

Invoke-Req "Obtener el docente por id" 200 GET "/api/teachers/$teacherId" $AdminToken $null | Out-Null
Invoke-Req "Listar todos los docentes" 200 GET "/api/teachers" $AdminToken $null | Out-Null

# /api/teachers/me resuelve al docente por el claim "email" del token: el
# administrador no es docente, asi que para el no hay ficha.
Invoke-Req "El administrador no tiene ficha de docente" 404 GET "/api/teachers/me" $AdminToken $null | Out-Null
Invoke-Req "El estudiante tampoco" 404 GET "/api/teachers/me" $StudentToken $null | Out-Null

$activos = Invoke-Req "Listar docentes ACTIVO" 200 GET "/api/teachers?status=ACTIVO" $AdminToken $null
Write-Host "          docentes ACTIVO: $(@($activos).Count)" -ForegroundColor DarkGray

Invoke-Req "Filtrar por estado en minusculas" 200 GET "/api/teachers?status=activo" $AdminToken $null | Out-Null

# ---------------------------------------------------------------
# UNICIDAD DE RUN Y EMAIL
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== UNICIDAD DE RUN Y EMAIL ===================================" -ForegroundColor Cyan

Invoke-Req "RUN repetido" 409 POST "/api/teachers" $AdminToken `
    (New-TeacherBody $run1 "Otra Persona" "otro.correo.$stamp@ipt.cl" "Ingeniero" "Magister") | Out-Null

Invoke-Req "Email repetido" 409 POST "/api/teachers" $AdminToken `
    (New-TeacherBody $run3 "Otra Persona" $mail1 "Ingeniero" "Magister") | Out-Null

Invoke-Req "Cambiar el RUN al de otro docente" 409 PUT "/api/teachers/$teacherId" $AdminToken `
    (New-TeacherBody $run2 "Ana Perez" $mail1 "Ingeniera en Informatica" "Magister en Ciencias de la Computacion") | Out-Null

Invoke-Req "Cambiar el email al de otro docente" 409 PUT "/api/teachers/$teacherId" $AdminToken `
    (New-TeacherBody $run1 "Ana Perez" $mail2 "Ingeniera en Informatica" "Magister en Ciencias de la Computacion") | Out-Null

# ---------------------------------------------------------------
# VALIDACIONES DE CAMPOS (400)
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== VALIDACIONES DE CAMPOS ====================================" -ForegroundColor Cyan

Invoke-Req "RUN con formato invalido" 400 POST "/api/teachers" $AdminToken `
    (New-TeacherBody "123-X" "Mal RUN" "mal.run.$stamp@ipt.cl" "Ingeniero" "Magister") | Out-Null

Invoke-Req "Email con formato invalido" 400 POST "/api/teachers" $AdminToken `
    (New-TeacherBody $run3 "Mal Email" "esto-no-es-un-email" "Ingeniero" "Magister") | Out-Null

Invoke-Req "Falta el titulo profesional" 400 POST "/api/teachers" $AdminToken @{
    run            = $run3
    fullName       = "Sin Titulo"
    email          = "sin.titulo.$stamp@ipt.cl"
    academicDegree = "Magister"
} | Out-Null

Invoke-Req "Falta el grado academico" 400 POST "/api/teachers" $AdminToken @{
    run               = $run3
    fullName          = "Sin Grado"
    email             = "sin.grado.$stamp@ipt.cl"
    professionalTitle = "Ingeniero"
} | Out-Null

Invoke-Req "Body vacio" 400 POST "/api/teachers" $AdminToken @{} | Out-Null

# ---------------------------------------------------------------
# ACTUALIZACION
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== ACTUALIZACION =============================================" -ForegroundColor Cyan

$updated = Invoke-Req "Actualizar nombre y grado academico" 200 PUT "/api/teachers/$teacherId" $AdminToken `
    (New-TeacherBody $run1 "Ana Maria Perez" $mail1 "Ingeniera en Informatica" "Doctora en Ciencias de la Computacion")

if ($updated -and $updated.fullName -eq "Ana Maria Perez" -and $updated.status -eq "ACTIVO") {
    Write-Host "  [OK]         El PUT no altero el estado del docente" -ForegroundColor Green
    $script:pass++
} else {
    Write-Host "  [FALLA]      El PUT devolvio nombre '$($updated.fullName)' y status '$($updated.status)'" -ForegroundColor Red
    $script:fail++
}

Invoke-Req "Actualizar un docente inexistente" 404 PUT "/api/teachers/999999" $AdminToken `
    (New-TeacherBody $run3 "Fantasma" "fantasma.$stamp@ipt.cl" "Ingeniero" "Magister") | Out-Null

Invoke-Req "Obtener un docente inexistente" 404 GET "/api/teachers/999999" $AdminToken $null | Out-Null

# ---------------------------------------------------------------
# CAMBIO DE ESTADO
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== CAMBIO DE ESTADO ==========================================" -ForegroundColor Cyan

$inactivo = Invoke-Req "Pasar a INACTIVO" 200 PATCH "/api/teachers/$teacherId/status?status=INACTIVO" $AdminToken $null
if ($inactivo -and $inactivo.status -eq "INACTIVO" -and $inactivo.fullName -eq "Ana Maria Perez") {
    Write-Host "  [OK]         El cambio de estado no altero los demas datos" -ForegroundColor Green
    $script:pass++
} else {
    Write-Host "  [FALLA]      Tras el PATCH quedo status '$($inactivo.status)' y nombre '$($inactivo.fullName)'" -ForegroundColor Red
    $script:fail++
}

$inactivos = Invoke-Req "Listar docentes INACTIVO" 200 GET "/api/teachers?status=INACTIVO" $AdminToken $null
Write-Host "          docentes INACTIVO: $(@($inactivos).Count)" -ForegroundColor DarkGray

Invoke-Req "Volver a ACTIVO" 200 PATCH "/api/teachers/$teacherId/status?status=ACTIVO" $AdminToken $null | Out-Null
Invoke-Req "Estado invalido" 409 PATCH "/api/teachers/$teacherId/status?status=JUBILADO" $AdminToken $null | Out-Null
Invoke-Req "Filtrar por un estado invalido" 409 GET "/api/teachers?status=JUBILADO" $AdminToken $null | Out-Null
Invoke-Req "Cambiar el estado de un docente inexistente" 404 PATCH "/api/teachers/999999/status?status=INACTIVO" $AdminToken $null | Out-Null

# ---------------------------------------------------------------
# SEGURIDAD
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== SEGURIDAD =================================================" -ForegroundColor Cyan

if ($StudentToken) {
    Invoke-Req "STUDENT puede leer docentes" 200 GET "/api/teachers" $StudentToken $null | Out-Null

    Invoke-Req "STUDENT no puede crear docentes" 403 POST "/api/teachers" $StudentToken `
        (New-TeacherBody $run3 "No Permitido" "no.permitido.$stamp@ipt.cl" "Ingeniero" "Magister") | Out-Null

    Invoke-Req "STUDENT no puede cambiar el estado" 403 PATCH "/api/teachers/$teacherId/status?status=INACTIVO" $StudentToken $null | Out-Null

    Invoke-Req "STUDENT no puede borrar docentes" 403 DELETE "/api/teachers/$teacherId" $StudentToken $null | Out-Null
} else {
    Write-Host "  [SKIP]        4 pruebas de rol STUDENT (sin token)" -ForegroundColor Yellow
}

Invoke-Req "Sin token" 401 GET "/api/teachers" "" $null | Out-Null

# ---------------------------------------------------------------
# BORRADO
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== BORRADO ===================================================" -ForegroundColor Cyan

Invoke-Req "ADMIN borra el segundo docente" 204 DELETE "/api/teachers/$teacher2Id" $AdminToken $null | Out-Null
Invoke-Req "Borrar el mismo docente otra vez" 404 DELETE "/api/teachers/$teacher2Id" $AdminToken $null | Out-Null
Invoke-Req "ADMIN borra el primer docente" 204 DELETE "/api/teachers/$teacherId" $AdminToken $null | Out-Null

# ---------------------------------------------------------------

Write-Host ""
Write-Host "===============================================================" -ForegroundColor Cyan
if ($script:fail -eq 0) {
    Write-Host ("  {0} OK, {1} FALLA" -f $script:pass, $script:fail) -ForegroundColor Green
} else {
    Write-Host ("  {0} OK, {1} FALLA" -f $script:pass, $script:fail) -ForegroundColor Red
}
Write-Host "===============================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Nota: 'no eliminar con secciones' y 'no pasar a INACTIVO con secciones" -ForegroundColor DarkGray
Write-Host "en periodo ABIERTO' quedan pendientes hasta la Epica 5 (Section)." -ForegroundColor DarkGray
Write-Host ""

if ($script:fail -gt 0) { exit 1 }
exit 0
