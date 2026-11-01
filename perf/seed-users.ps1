# SIGA IPT - Poblar el sistema con N estudiantes para pruebas de carga.
#
# Crea, de forma idempotente y en dos lugares:
#   1. Usuarios en Keycloak (realm siga-realm) con el rol STUDENT, todos con la
#      misma password, via la Admin REST API.
#   2. Los estudiantes equivalentes en SIGA (POST /api/students), con el email
#      del usuario de Keycloak: es el claim que usa el backend para resolver
#      quien es "me" en /api/students/me, /api/enrollments/me, etc.
#
# Opcionalmente los matricula (-Matriculate) en el periodo ABIERTO mas reciente,
# que es requisito para que GET /api/enrollments/available devuelva algo.
#
# Al final escribe perf/users.json, que es lo que lee perf/load-test.js.
# La password NO se escribe en el archivo: va por variable de entorno.
#
# Uso desde cmd (con el stack de Docker arriba):
#   set KEYCLOAK_ADMIN=admin
#   set KEYCLOAK_ADMIN_PASSWORD=...        REM admin de la consola de Keycloak
#   set KC_CLIENT_ID=siga-frontend         REM cliente con direct access grants
#   set KC_ADMIN_USER=admin                REM username (no el email) del ADMIN del realm
#   set KC_ADMIN_PASS=...
#   set PERF_USER_PASS=Perf.2026
#   powershell -ExecutionPolicy Bypass -File perf\seed-users.ps1 -Count 100 -Matriculate
#
# Borrar lo creado: perf\seed-users.ps1 -Count 100 -Remove

param(
    [int]$Count = 100,

    # El stack de Docker publica todo por nginx: la API en /api y Keycloak en /auth.
    [string]$BaseUrl      = "http://localhost",
    [string]$KeycloakUrl,
    [string]$Realm        = "siga-realm",

    # Admin de la consola de Keycloak (realm master), para crear usuarios.
    [string]$KcAdminUser  = $env:KEYCLOAK_ADMIN,
    [string]$KcAdminPass  = $env:KEYCLOAK_ADMIN_PASSWORD,

    # Usuario con rol ADMIN dentro del realm, para crear los estudiantes.
    [string]$ClientId     = $env:KC_CLIENT_ID,
    [string]$ClientSecret = $env:KC_CLIENT_SECRET,
    [string]$AdminUser    = $env:KC_ADMIN_USER,
    [string]$AdminPass    = $env:KC_ADMIN_PASS,

    # La password de todos los usuarios de prueba.
    [string]$UserPassword = $env:PERF_USER_PASS,

    # Carrera en la que se inscriben; por defecto, la primera que exista.
    [string]$CareerCode,

    [switch]$Matriculate,
    [switch]$Remove
)

$ErrorActionPreference = "Stop"
if (-not $KeycloakUrl) { $KeycloakUrl = "$BaseUrl/auth" }

$prefix   = "perf-student"
$mailHost = "perf.siga.cl"
$outFile  = Join-Path $PSScriptRoot "users.json"

function Require-Param {
    param([string]$Value, [string]$Name)
    if (-not $Value) {
        Write-Host "Falta $Name. Ver el encabezado del script." -ForegroundColor Red
        exit 1
    }
}

Require-Param $KcAdminUser  "KEYCLOAK_ADMIN"
Require-Param $KcAdminPass  "KEYCLOAK_ADMIN_PASSWORD"
Require-Param $ClientId     "KC_CLIENT_ID"
Require-Param $AdminUser    "KC_ADMIN_USER"
Require-Param $AdminPass    "KC_ADMIN_PASS"
if (-not $Remove) { Require-Param $UserPassword "PERF_USER_PASS" }

# ---------------------------------------------------------------
# Tokens
# ---------------------------------------------------------------

function Get-Token {
    param([string]$TokenRealm, [string]$Client, [string]$User, [string]$Pass, [string]$Secret)

    $form = @{
        grant_type = "password"
        client_id  = $Client
        username   = $User
        password   = $Pass
    }
    if ($Secret) { $form["client_secret"] = $Secret }

    try {
        $res = Invoke-RestMethod -Method Post -Body $form `
            -Uri "$KeycloakUrl/realms/$TokenRealm/protocol/openid-connect/token"
        return $res.access_token
    } catch {
        Write-Host "No se pudo obtener el token de $User en el realm $TokenRealm : $($_.Exception.Message)" -ForegroundColor Red
        exit 1
    }
}

Write-Host "Autenticando..." -ForegroundColor Cyan
$kcToken    = Get-Token "master" "admin-cli" $KcAdminUser $KcAdminPass $null
$adminToken = Get-Token $Realm $ClientId $AdminUser $AdminPass $ClientSecret
Write-Host "  tokens de Keycloak (master) y de SIGA (ADMIN) obtenidos" -ForegroundColor DarkGray

$kcHeaders    = @{ Authorization = "Bearer $kcToken" }
$sigaHeaders  = @{ Authorization = "Bearer $adminToken" }
$adminApi     = "$KeycloakUrl/admin/realms/$Realm"

# ---------------------------------------------------------------
# Helpers HTTP que no abortan ante un error esperado
# ---------------------------------------------------------------

function Try-Rest {
    param([string]$Method, [string]$Uri, $Headers, $Body, [string]$RawBody)

    $reqArgs = @{ Method = $Method; Uri = $Uri; Headers = $Headers; ErrorAction = "Stop" }
    # ConvertTo-Json colapsa un array de un elemento en objeto, asi que los
    # cuerpos que tienen que viajar como lista llegan ya serializados.
    if ($RawBody) {
        $reqArgs["Body"]        = $RawBody
        $reqArgs["ContentType"] = "application/json"
    } elseif ($null -ne $Body) {
        $reqArgs["Body"]        = ($Body | ConvertTo-Json -Compress -Depth 6)
        $reqArgs["ContentType"] = "application/json"
    }
    try {
        return @{ ok = $true; data = (Invoke-RestMethod @reqArgs); status = 200 }
    } catch {
        $status = 0
        if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode.value__ }
        return @{ ok = $false; data = $null; status = $status }
    }
}

function Get-KcUser {
    param([string]$Username)
    $res = Try-Rest GET "$adminApi/users?username=$Username&exact=true" $kcHeaders $null
    if ($res.ok) { return @($res.data) | Select-Object -First 1 }
    return $null
}

# ---------------------------------------------------------------
# Borrado
# ---------------------------------------------------------------

if ($Remove) {
    Write-Host "Borrando los usuarios de prueba..." -ForegroundColor Cyan
    $goneKc = 0
    $goneDb = 0

    # Los estudiantes con inscripciones o historial no se borran (409): es la
    # regla del sistema, no un error del script.
    $students = (Try-Rest GET "$BaseUrl/api/students" $sigaHeaders $null).data
    foreach ($s in @($students)) {
        if ($s.email -like "*@$mailHost") {
            if ((Try-Rest DELETE "$BaseUrl/api/students/$($s.id)" $sigaHeaders $null).ok) { $goneDb++ }
        }
    }
    foreach ($i in 1..$Count) {
        $u = Get-KcUser ("{0}-{1:D4}" -f $prefix, $i)
        if ($u) {
            if ((Try-Rest DELETE "$adminApi/users/$($u.id)" $kcHeaders $null).ok) { $goneKc++ }
        }
    }
    Write-Host "  $goneKc usuarios de Keycloak y $goneDb estudiantes borrados" -ForegroundColor Green
    Write-Host "  (los estudiantes con inscripciones o historial quedan: el 409 los protege)" -ForegroundColor DarkGray
    if (Test-Path $outFile) { Remove-Item $outFile }
    exit 0
}

# ---------------------------------------------------------------
# 1. La carrera y el periodo
# ---------------------------------------------------------------

$careers = @((Try-Rest GET "$BaseUrl/api/careers" $sigaHeaders $null).data)
if ($careers.Count -eq 0) {
    Write-Host "No hay carreras: corre antes deploy/seed.sh." -ForegroundColor Red
    exit 1
}
$career = $careers[0]
if ($CareerCode) {
    $career = @($careers) | Where-Object { $_.code -eq $CareerCode } | Select-Object -First 1
    if (-not $career) { Write-Host "No existe la carrera $CareerCode." -ForegroundColor Red; exit 1 }
}
Write-Host "Carrera: $($career.code) - $($career.name)" -ForegroundColor Cyan

$periodId = $null
$periods = @((Try-Rest GET "$BaseUrl/api/academic-periods?status=ABIERTO" $sigaHeaders $null).data)
if ($periods.Count -gt 0) {
    $periodId = (@($periods) | Sort-Object code -Descending | Select-Object -First 1).id
    Write-Host "Periodo ABIERTO: $((@($periods) | Sort-Object code -Descending | Select-Object -First 1).code) (id $periodId)" -ForegroundColor Cyan
} else {
    Write-Host "No hay periodos ABIERTO: la prueba de lectura funciona igual, pero sin oferta." -ForegroundColor Yellow
}

# El rol STUDENT del realm, para asignarlo a cada usuario nuevo.
$studentRole = (Try-Rest GET "$adminApi/roles/STUDENT" $kcHeaders $null).data
if (-not $studentRole) {
    Write-Host "El realm $Realm no tiene el rol STUDENT." -ForegroundColor Red
    exit 1
}

# Los estudiantes que ya existen, por email: evita un GET por cada uno.
$existing = @{}
foreach ($s in @((Try-Rest GET "$BaseUrl/api/students" $sigaHeaders $null).data)) {
    $existing[$s.email.ToLower()] = $s
}

# ---------------------------------------------------------------
# 2. Crear usuarios y estudiantes
# ---------------------------------------------------------------

Write-Host ""
Write-Host "Creando $Count estudiantes de prueba..." -ForegroundColor Cyan

$users        = New-Object System.Collections.ArrayList
$createdKc    = 0
$createdDb    = 0
$reusedKc     = 0
$reusedDb     = 0
$matriculated = 0
$failed       = 0

foreach ($i in 1..$Count) {
    $username = "{0}-{1:D4}" -f $prefix, $i
    $email    = "{0}.{1:D4}@{2}" -f $prefix, $i, $mailHost
    # RUN sintetico: 8 digitos que empiezan en 9, fuera del rango de los datos demo
    $run      = "9{0:D7}-0" -f $i

    # --- Keycloak ---
    $kcUser = Get-KcUser $username
    if ($kcUser) {
        $reusedKc++
    } else {
        $create = Try-Rest POST "$adminApi/users" $kcHeaders @{
            username      = $username
            email         = $email
            firstName     = "Perf"
            lastName      = ("Student {0}" -f $i)
            enabled       = $true
            emailVerified = $true
            credentials   = @(@{ type = "password"; value = $UserPassword; temporary = $false })
        }
        if (-not $create.ok) {
            Write-Host "  [FALLA] usuario $username en Keycloak (HTTP $($create.status))" -ForegroundColor Red
            $failed++
            continue
        }
        $kcUser = Get-KcUser $username
        $createdKc++
    }

    # El rol es idempotente: volver a asignarlo no cambia nada.
    $roleBody = "[" + (@{ id = $studentRole.id; name = $studentRole.name } | ConvertTo-Json -Compress) + "]"
    Try-Rest POST "$adminApi/users/$($kcUser.id)/role-mappings/realm" $kcHeaders $null -RawBody $roleBody | Out-Null

    # --- SIGA ---
    $student = $existing[$email.ToLower()]
    if ($student) {
        $reusedDb++
    } else {
        $post = Try-Rest POST "$BaseUrl/api/students" $sigaHeaders @{
            run      = $run
            fullName = ("Perf Student {0:D4}" -f $i)
            email    = $email
            careerId = $career.id
        }
        if (-not $post.ok) {
            Write-Host "  [FALLA] estudiante $email en SIGA (HTTP $($post.status))" -ForegroundColor Red
            $failed++
            continue
        }
        $student = $post.data
        $createdDb++
    }

    # --- Matricula (requisito de GET /api/enrollments/available) ---
    if ($Matriculate -and $periodId) {
        $put = Try-Rest PUT "$BaseUrl/api/student-period-status" $sigaHeaders @{
            studentId        = $student.id
            academicPeriodId = $periodId
            enrollmentStatus = "MATRICULATED"
        }
        if ($put.ok) { $matriculated++ }
    }

    [void]$users.Add(@{ username = $username; email = $email; studentId = $student.id })

    if ($i % 25 -eq 0) { Write-Host "  $i/$Count" -ForegroundColor DarkGray }
}

# ---------------------------------------------------------------
# 3. El archivo que lee k6
# ---------------------------------------------------------------

$manifest = @{
    baseUrl     = $BaseUrl
    keycloakUrl = $KeycloakUrl
    realm       = $Realm
    clientId    = $ClientId
    periodId    = $periodId
    users       = @($users)
} | ConvertTo-Json -Depth 5

# Sin BOM: k6 hace JSON.parse del archivo tal cual y el BOM lo rompe.
[System.IO.File]::WriteAllText($outFile, $manifest, (New-Object System.Text.UTF8Encoding($false)))

Write-Host ""
Write-Host "Keycloak:   $createdKc creados, $reusedKc reutilizados" -ForegroundColor Green
Write-Host "SIGA:       $createdDb creados, $reusedDb reutilizados" -ForegroundColor Green
if ($Matriculate) { Write-Host "Matricula:  $matriculated en el periodo $periodId" -ForegroundColor Green }
if ($failed -gt 0) { Write-Host "Fallaron:   $failed" -ForegroundColor Red }
Write-Host "Escrito:    $outFile ($($users.Count) usuarios)" -ForegroundColor Green
Write-Host ""
Write-Host "Siguiente: la prueba de carga (ver perf/README.md)" -ForegroundColor Cyan
