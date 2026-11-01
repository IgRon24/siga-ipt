# SIGA IPT - Prueba manual de Epica 3: Asignaturas y prerrequisitos
#
# Version nativa de Windows: no necesita Git Bash ni IntelliJ Ultimate.
#
# Uso desde cmd, con tokens a mano:
#   set ADMIN_TOKEN=eyJ...
#   set STUDENT_TOKEN=eyJ...
#   powershell -ExecutionPolicy Bypass -File http\prerequisites.ps1
#
# Uso desde cmd, con login automatico contra Keycloak:
#   set KC_CLIENT_ID=siga-client
#   set KC_ADMIN_USER=admin-siga
#   set KC_ADMIN_PASS=1234
#   set KC_STUDENT_USER=student-siga
#   set KC_STUDENT_PASS=1234
#   powershell -ExecutionPolicy Bypass -File http\prerequisites.ps1
#
# Tambien acepta los valores como parametros:
#   powershell -ExecutionPolicy Bypass -File http\prerequisites.ps1 -AdminToken eyJ... -StudentToken eyJ...

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

# Codigos unicos por corrida: el codigo de carrera son exactamente 5 digitos.
$suffix     = Get-Date -Format "HHmmss"
$careerCode = "1" + (Get-Date -Format "mmss")

function Get-KeycloakToken {
    param([string]$User, [string]$Pass, [string]$Label)

    $form = @{
        grant_type = "password"
        client_id  = $ClientId
        username   = $User
        password   = $Pass
    }
    if ($ClientSecret) { $form["client_secret"] = $ClientSecret }

    $url = "$KeycloakUrl/realms/$Realm/protocol/openid-connect/token"
    Write-Host "Obteniendo token de $Label desde Keycloak..."
    try {
        $response = Invoke-RestMethod -Uri $url -Method Post -Body $form
        return $response.access_token
    } catch {
        Write-Host "  No se pudo obtener el token de $Label : $($_.Exception.Message)" -ForegroundColor Red
        return $null
    }
}

# Pide el status y el body de una peticion, sin que un 4xx corte el script.
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

function New-SubjectBody {
    param([string]$Code, [string]$Name, [int]$Semester, [long]$PlanId)
    return @{
        code         = $Code
        name         = $Name
        semester     = $Semester
        telTheory    = 2
        telExercises = 2
        telLab       = 2
        sct          = 5
        studyPlanId  = $PlanId
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
    Write-Host "AVISO: sin token de STUDENT, las 3 pruebas de rol se van a saltar." -ForegroundColor Yellow
}

# ---------------------------------------------------------------
# PREPARACION
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== PREPARACION ===============================================" -ForegroundColor Cyan

$career = Invoke-Req "Crear carrera $careerCode" 201 POST "/api/careers" $AdminToken @{
    code        = $careerCode
    name        = "Tecnico Analista Programador"
    description = "Carrera tecnica de cuatro semestres"
    status      = "ACTIVA"
}
if (-not $career) { Write-Host "No se pudo crear la carrera, se aborta." -ForegroundColor Red; exit 1 }
$careerId = $career.id

$plan = Invoke-Req "Crear plan de estudios VIGENTE" 201 POST "/api/study-plans" $AdminToken @{
    identifier = "P1-$suffix"
    name       = "Plan de estudios 2021.3"
    status     = "VIGENTE"
    careerId   = $careerId
}
$planId = $plan.id

$tap101 = (Invoke-Req "Crear TAP101 (semestre 1)" 201 POST "/api/subjects" $AdminToken `
    (New-SubjectBody "TAP101-$suffix" "Programacion I" 1 $planId)).id

$tap201 = (Invoke-Req "Crear TAP201 (semestre 2)" 201 POST "/api/subjects" $AdminToken `
    (New-SubjectBody "TAP201-$suffix" "Programacion II" 2 $planId)).id

$tap202 = (Invoke-Req "Crear TAP202 (semestre 2)" 201 POST "/api/subjects" $AdminToken `
    (New-SubjectBody "TAP202-$suffix" "Bases de Datos" 2 $planId)).id

$tap102 = (Invoke-Req "Crear TAP102 (semestre 1)" 201 POST "/api/subjects" $AdminToken `
    (New-SubjectBody "TAP102-$suffix" "Matematica I" 1 $planId)).id

$tap103 = (Invoke-Req "Crear TAP103 (semestre 1)" 201 POST "/api/subjects" $AdminToken `
    (New-SubjectBody "TAP103-$suffix" "Ingles I" 1 $planId)).id

$tap104 = (Invoke-Req "Crear TAP104 (semestre 1)" 201 POST "/api/subjects" $AdminToken `
    (New-SubjectBody "TAP104-$suffix" "Algebra I" 1 $planId)).id

# ---------------------------------------------------------------
# CASOS FELICES
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== CASOS FELICES =============================================" -ForegroundColor Cyan

Invoke-Req "TAP101 (sem 1) como prerrequisito de TAP201 (sem 2)" 201 POST `
    "/api/subjects/$tap201/prerequisites" $AdminToken @{ prerequisiteSubjectId = $tap101 } | Out-Null

$list = Invoke-Req "Listar prerrequisitos de TAP201" 200 GET `
    "/api/subjects/$tap201/prerequisites" $AdminToken $null
Write-Host "          prerrequisitos: $(@($list).Count)" -ForegroundColor DarkGray

Invoke-Req "Listar prerrequisitos de TAP101 (vacio)" 200 GET `
    "/api/subjects/$tap101/prerequisites" $AdminToken $null | Out-Null

# ---------------------------------------------------------------
# CASOS DE RECHAZO
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== CASOS DE RECHAZO ==========================================" -ForegroundColor Cyan

Invoke-Req "Prerrequisito duplicado" 409 POST `
    "/api/subjects/$tap201/prerequisites" $AdminToken @{ prerequisiteSubjectId = $tap101 } | Out-Null

Invoke-Req "Prerrequisito del mismo semestre" 409 POST `
    "/api/subjects/$tap201/prerequisites" $AdminToken @{ prerequisiteSubjectId = $tap202 } | Out-Null

Invoke-Req "Prerrequisito de semestre posterior" 409 POST `
    "/api/subjects/$tap101/prerequisites" $AdminToken @{ prerequisiteSubjectId = $tap201 } | Out-Null

Invoke-Req "Asignatura prerrequisito de si misma" 409 POST `
    "/api/subjects/$tap201/prerequisites" $AdminToken @{ prerequisiteSubjectId = $tap201 } | Out-Null

Invoke-Req "Prerrequisito inexistente" 404 POST `
    "/api/subjects/$tap201/prerequisites" $AdminToken @{ prerequisiteSubjectId = 999999 } | Out-Null

Invoke-Req "Asignatura inexistente" 404 POST `
    "/api/subjects/999999/prerequisites" $AdminToken @{ prerequisiteSubjectId = $tap101 } | Out-Null

Invoke-Req "Body sin prerequisiteSubjectId" 400 POST `
    "/api/subjects/$tap201/prerequisites" $AdminToken @{} | Out-Null

Invoke-Req "No borrar TAP101: es prerrequisito de TAP201" 409 DELETE `
    "/api/subjects/$tap101" $AdminToken $null | Out-Null

# ---------------------------------------------------------------
# REGLA DE OTRO PLAN DE ESTUDIOS
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== REGLA DE OTRO PLAN DE ESTUDIOS ============================" -ForegroundColor Cyan

$planId2 = (Invoke-Req "Crear segundo plan NO_VIGENTE" 201 POST "/api/study-plans" $AdminToken @{
    identifier = "P2-$suffix"
    name       = "Plan de estudios 2022.3"
    status     = "NO_VIGENTE"
    careerId   = $careerId
}).id

$otherSubject = (Invoke-Req "Crear asignatura en el otro plan" 201 POST "/api/subjects" $AdminToken `
    (New-SubjectBody "OTR101-$suffix" "Asignatura de otro plan" 1 $planId2)).id

Invoke-Req "Prerrequisito de otro plan de estudios" 409 POST `
    "/api/subjects/$tap201/prerequisites" $AdminToken @{ prerequisiteSubjectId = $otherSubject } | Out-Null

# ---------------------------------------------------------------
# LIMITE DE 3 PRERREQUISITOS
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== LIMITE DE 3 PRERREQUISITOS ================================" -ForegroundColor Cyan

Invoke-Req "Segundo prerrequisito de TAP201" 201 POST `
    "/api/subjects/$tap201/prerequisites" $AdminToken @{ prerequisiteSubjectId = $tap102 } | Out-Null

Invoke-Req "Tercer prerrequisito de TAP201 (limite permitido)" 201 POST `
    "/api/subjects/$tap201/prerequisites" $AdminToken @{ prerequisiteSubjectId = $tap103 } | Out-Null

Invoke-Req "Cuarto prerrequisito de TAP201" 409 POST `
    "/api/subjects/$tap201/prerequisites" $AdminToken @{ prerequisiteSubjectId = $tap104 } | Out-Null

# ---------------------------------------------------------------
# SEGURIDAD
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== SEGURIDAD =================================================" -ForegroundColor Cyan

if ($StudentToken) {
    Invoke-Req "STUDENT puede leer prerrequisitos" 200 GET `
        "/api/subjects/$tap201/prerequisites" $StudentToken $null | Out-Null

    Invoke-Req "STUDENT no puede crear prerrequisitos" 403 POST `
        "/api/subjects/$tap201/prerequisites" $StudentToken @{ prerequisiteSubjectId = $tap104 } | Out-Null

    Invoke-Req "STUDENT no puede borrar prerrequisitos" 403 DELETE `
        "/api/subjects/$tap201/prerequisites/$tap101" $StudentToken $null | Out-Null
} else {
    Write-Host "  [SKIP]        3 pruebas de rol STUDENT (sin token)" -ForegroundColor Yellow
}

Invoke-Req "Sin token" 401 GET "/api/subjects/$tap201/prerequisites" "" $null | Out-Null

# ---------------------------------------------------------------
# BORRADO
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== BORRADO ===================================================" -ForegroundColor Cyan

Invoke-Req "ADMIN borra el prerrequisito TAP101 de TAP201" 204 DELETE `
    "/api/subjects/$tap201/prerequisites/$tap101" $AdminToken $null | Out-Null

Invoke-Req "Borrar un prerrequisito que ya no existe" 404 DELETE `
    "/api/subjects/$tap201/prerequisites/$tap101" $AdminToken $null | Out-Null

Invoke-Req "TAP101 ya se puede borrar" 204 DELETE `
    "/api/subjects/$tap101" $AdminToken $null | Out-Null

$final = Invoke-Req "TAP201 quedo con 2 prerrequisitos" 200 GET `
    "/api/subjects/$tap201/prerequisites" $AdminToken $null
Write-Host "          prerrequisitos: $(@($final).Count)" -ForegroundColor DarkGray

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

if ($script:fail -gt 0) { exit 1 }
exit 0
