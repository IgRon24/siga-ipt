# SIGA IPT - Prueba manual de Epica 1: Gestion de estudiantes
#
# El script crea sus propias carreras y planes de estudio (fixtures), prueba las
# reglas de negocio de /api/students y al final limpia todo lo que creo.
#
# Uso desde cmd (login automatico contra Keycloak):
#   set KC_CLIENT_ID=tu-client-id
#   set KC_ADMIN_USER=tu-usuario-admin
#   set KC_ADMIN_PASS=tu-password
#   set KC_STUDENT_USER=tu-usuario-student
#   set KC_STUDENT_PASS=tu-password
#   powershell -ExecutionPolicy Bypass -File http\students.ps1
#
# Uso desde cmd (tokens a mano):
#   set ADMIN_TOKEN=eyJ...
#   set STUDENT_TOKEN=eyJ...
#   powershell -ExecutionPolicy Bypass -File http\students.ps1

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

# RUN, email y codigos de carrera unicos por corrida.
$stamp = Get-Date -Format "HHmmss"
$short = $stamp.Substring(2, 4)              # mmss, para los codigos de 5 digitos
$run1  = ("1" + $stamp + "1").Substring(0, 8) + "-9"
$run2  = ("2" + $stamp + "2").Substring(0, 8) + "-7"
$run3  = ("3" + $stamp + "3").Substring(0, 8) + "-5"
$run4  = ("4" + $stamp + "4").Substring(0, 8) + "-3"
$mail1 = "pedro.soto.$stamp@ipt.cl"
$mail2 = "maria.rojas.$stamp@ipt.cl"

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

# GET /api/students/me identifica al estudiante por el email del token.
function Get-JwtEmail {
    param([string]$Token)
    try {
        $payload = $Token.Split(".")[1].Replace("-", "+").Replace("_", "/")
        switch ($payload.Length % 4) {
            2 { $payload = $payload + "==" }
            3 { $payload = $payload + "=" }
        }
        $json = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($payload))
        return ($json | ConvertFrom-Json).email
    } catch {
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

function Assert-True {
    param([bool]$Condition, [string]$Desc, [string]$Detail)
    if ($Condition) {
        Write-Host "  [OK]         $Desc" -ForegroundColor Green
        $script:pass++
    } else {
        Write-Host "  [FALLA]      $Desc" -ForegroundColor Red
        if ($Detail) { Write-Host "          $Detail" -ForegroundColor DarkGray }
        $script:fail++
    }
}

function New-StudentBody {
    param([string]$Run, [string]$Name, [string]$Email, [long]$CareerId)
    return @{
        run      = $Run
        fullName = $Name
        email    = $Email
        careerId = $CareerId
    }
}

function New-CareerBody {
    param([string]$Code, [string]$Name, [string]$Status)
    return @{
        code        = $Code
        name        = $Name
        description = "Carrera creada por students.ps1"
        status      = $Status
    }
}

function New-PlanBody {
    param([string]$Identifier, [string]$Name, [string]$Status, [long]$CareerId)
    return @{
        identifier = $Identifier
        name       = $Name
        status     = $Status
        careerId   = $CareerId
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
# FIXTURES: carreras y planes de estudio
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== FIXTURES: CARRERAS Y PLANES ===============================" -ForegroundColor Cyan

$careerA = Invoke-Req "Crear carrera ACTIVA con plan VIGENTE" 201 POST "/api/careers" $AdminToken `
    (New-CareerBody ("7" + $short) "Carrera A $stamp" "ACTIVA")
if (-not $careerA) { Write-Host "No se pudo crear la carrera, se aborta." -ForegroundColor Red; exit 1 }
$careerAId = $careerA.id

$planA = Invoke-Req "Crear plan VIGENTE de la carrera A" 201 POST "/api/study-plans" $AdminToken `
    (New-PlanBody "2026.1" "Plan 2026 carrera A" "VIGENTE" $careerAId)
if (-not $planA) { Write-Host "No se pudo crear el plan, se aborta." -ForegroundColor Red; exit 1 }
$planAId = $planA.id

$careerB = Invoke-Req "Crear carrera ACTIVA sin ningun plan" 201 POST "/api/careers" $AdminToken `
    (New-CareerBody ("8" + $short) "Carrera B $stamp" "ACTIVA")
$careerBId = $careerB.id

$careerC = Invoke-Req "Crear carrera que quedara INACTIVA" 201 POST "/api/careers" $AdminToken `
    (New-CareerBody ("9" + $short) "Carrera C $stamp" "ACTIVA")
$careerCId = $careerC.id

$planC = Invoke-Req "Crear plan VIGENTE de la carrera C" 201 POST "/api/study-plans" $AdminToken `
    (New-PlanBody "2026.1" "Plan 2026 carrera C" "VIGENTE" $careerCId)
$planCId = $planC.id

Invoke-Req "Pasar la carrera C a INACTIVA" 200 PUT "/api/careers/$careerCId" $AdminToken `
    (New-CareerBody ("9" + $short) "Carrera C $stamp" "INACTIVA") | Out-Null

$careerD = Invoke-Req "Crear carrera D para el traslado" 201 POST "/api/careers" $AdminToken `
    (New-CareerBody ("6" + $short) "Carrera D $stamp" "ACTIVA")
$careerDId = $careerD.id

$planD = Invoke-Req "Crear plan VIGENTE de la carrera D" 201 POST "/api/study-plans" $AdminToken `
    (New-PlanBody "2026.1" "Plan 2026 carrera D" "VIGENTE" $careerDId)
$planDId = $planD.id

# ---------------------------------------------------------------
# ALTA Y ESTADO INICIAL
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== ALTA Y ESTADO INICIAL =====================================" -ForegroundColor Cyan

$student = Invoke-Req "Registrar estudiante $run1" 201 POST "/api/students" $AdminToken `
    (New-StudentBody $run1 "Pedro Soto" $mail1 $careerAId)
if (-not $student) { Write-Host "No se pudo crear el estudiante, se aborta." -ForegroundColor Red; exit 1 }
$studentId = $student.id

Assert-True ($student.status -eq "REGULAR") `
    "El estudiante quedo en estado REGULAR automaticamente" "llego status '$($student.status)'"

Assert-True ($student.studyPlanId -eq $planAId) `
    "Quedo asociado al plan VIGENTE de su carrera" "llego studyPlanId '$($student.studyPlanId)', se esperaba $planAId"

$student2 = Invoke-Req "Registrar un segundo estudiante" 201 POST "/api/students" $AdminToken `
    (New-StudentBody $run2 "Maria Rojas" $mail2 $careerAId)
$student2Id = $student2.id

Invoke-Req "Obtener el estudiante por id" 200 GET "/api/students/$studentId" $AdminToken $null | Out-Null
Invoke-Req "Listar todos los estudiantes" 200 GET "/api/students" $AdminToken $null | Out-Null

$regulares = Invoke-Req "Listar estudiantes REGULAR" 200 GET "/api/students?status=REGULAR" $AdminToken $null
Write-Host "          estudiantes REGULAR: $(@($regulares).Count)" -ForegroundColor DarkGray

Invoke-Req "Filtrar por estado en minusculas" 200 GET "/api/students?status=regular" $AdminToken $null | Out-Null

$deCarreraA = Invoke-Req "Listar estudiantes de la carrera A" 200 GET "/api/students?careerId=$careerAId" $AdminToken $null
Assert-True (@($deCarreraA).Count -eq 2) `
    "La carrera A tiene los 2 estudiantes creados" "llegaron $(@($deCarreraA).Count)"

Invoke-Req "Listar estudiantes de una carrera inexistente" 404 GET "/api/students?careerId=999999" $AdminToken $null | Out-Null

# ---------------------------------------------------------------
# CARRERA Y PLAN DE ESTUDIOS
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== CARRERA Y PLAN DE ESTUDIOS ================================" -ForegroundColor Cyan

Invoke-Req "Carrera sin plan VIGENTE" 409 POST "/api/students" $AdminToken `
    (New-StudentBody $run3 "Sin Plan" "sin.plan.$stamp@ipt.cl" $careerBId) | Out-Null

Invoke-Req "Carrera INACTIVA no recibe estudiantes" 409 POST "/api/students" $AdminToken `
    (New-StudentBody $run3 "Carrera Inactiva" "inactiva.$stamp@ipt.cl" $careerCId) | Out-Null

Invoke-Req "Carrera inexistente" 404 POST "/api/students" $AdminToken `
    (New-StudentBody $run3 "Sin Carrera" "sin.carrera.$stamp@ipt.cl" 999999) | Out-Null

# Regla: un plan nuevo no mueve a los estudiantes que ya estaban en el plan anterior.
$planA2 = Invoke-Req "Crear un plan nuevo VIGENTE en la carrera A" 201 POST "/api/study-plans" $AdminToken `
    (New-PlanBody "2026.2" "Plan 2026.2 carrera A" "VIGENTE" $careerAId)
$planA2Id = $planA2.id

$sigue = Invoke-Req "Releer el primer estudiante" 200 GET "/api/students/$studentId" $AdminToken $null
Assert-True ($sigue.studyPlanId -eq $planAId) `
    "El plan nuevo no cambio el plan del estudiante ya registrado" "quedo con studyPlanId '$($sigue.studyPlanId)', se esperaba $planAId"

# ---------------------------------------------------------------
# UNICIDAD DE RUN Y EMAIL
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== UNICIDAD DE RUN Y EMAIL ===================================" -ForegroundColor Cyan

Invoke-Req "RUN repetido" 409 POST "/api/students" $AdminToken `
    (New-StudentBody $run1 "Otra Persona" "otro.correo.$stamp@ipt.cl" $careerAId) | Out-Null

Invoke-Req "Email repetido" 409 POST "/api/students" $AdminToken `
    (New-StudentBody $run3 "Otra Persona" $mail1 $careerAId) | Out-Null

Invoke-Req "Cambiar el RUN al de otro estudiante" 409 PUT "/api/students/$studentId" $AdminToken `
    (New-StudentBody $run2 "Pedro Soto" $mail1 $careerAId) | Out-Null

Invoke-Req "Cambiar el email al de otro estudiante" 409 PUT "/api/students/$studentId" $AdminToken `
    (New-StudentBody $run1 "Pedro Soto" $mail2 $careerAId) | Out-Null

# ---------------------------------------------------------------
# VALIDACIONES DE CAMPOS (400)
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== VALIDACIONES DE CAMPOS ====================================" -ForegroundColor Cyan

Invoke-Req "RUN con formato invalido" 400 POST "/api/students" $AdminToken `
    (New-StudentBody "123-X" "Mal RUN" "mal.run.$stamp@ipt.cl" $careerAId) | Out-Null

Invoke-Req "Email con formato invalido" 400 POST "/api/students" $AdminToken `
    (New-StudentBody $run3 "Mal Email" "esto-no-es-un-email" $careerAId) | Out-Null

Invoke-Req "Falta la carrera" 400 POST "/api/students" $AdminToken @{
    run      = $run3
    fullName = "Sin Carrera"
    email    = "falta.carrera.$stamp@ipt.cl"
} | Out-Null

Invoke-Req "Falta el nombre completo" 400 POST "/api/students" $AdminToken @{
    run      = $run3
    email    = "falta.nombre.$stamp@ipt.cl"
    careerId = $careerAId
} | Out-Null

Invoke-Req "Body vacio" 400 POST "/api/students" $AdminToken @{} | Out-Null

# ---------------------------------------------------------------
# ACTUALIZACION Y TRASLADO DE CARRERA
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== ACTUALIZACION Y TRASLADO ==================================" -ForegroundColor Cyan

$updated = Invoke-Req "Actualizar el nombre del estudiante" 200 PUT "/api/students/$studentId" $AdminToken `
    (New-StudentBody $run1 "Pedro Pablo Soto" $mail1 $careerAId)

Assert-True ($updated -and $updated.fullName -eq "Pedro Pablo Soto" -and $updated.status -eq "REGULAR" -and $updated.studyPlanId -eq $planAId) `
    "El PUT no altero el estado ni el plan del estudiante" "nombre '$($updated.fullName)', status '$($updated.status)', plan '$($updated.studyPlanId)'"

$trasladado = Invoke-Req "Trasladar al segundo estudiante a la carrera D" 200 PUT "/api/students/$student2Id" $AdminToken `
    (New-StudentBody $run2 "Maria Rojas" $mail2 $careerDId)

Assert-True ($trasladado -and $trasladado.careerId -eq $careerDId -and $trasladado.studyPlanId -eq $planDId) `
    "El traslado reasigno el plan VIGENTE de la carrera nueva" "carrera '$($trasladado.careerId)', plan '$($trasladado.studyPlanId)'"

Invoke-Req "Trasladar a una carrera INACTIVA" 409 PUT "/api/students/$studentId" $AdminToken `
    (New-StudentBody $run1 "Pedro Pablo Soto" $mail1 $careerCId) | Out-Null

Invoke-Req "Trasladar a una carrera sin plan VIGENTE" 409 PUT "/api/students/$studentId" $AdminToken `
    (New-StudentBody $run1 "Pedro Pablo Soto" $mail1 $careerBId) | Out-Null

Invoke-Req "Trasladar a una carrera inexistente" 404 PUT "/api/students/$studentId" $AdminToken `
    (New-StudentBody $run1 "Pedro Pablo Soto" $mail1 999999) | Out-Null

Invoke-Req "Actualizar un estudiante inexistente" 404 PUT "/api/students/999999" $AdminToken `
    (New-StudentBody $run3 "Fantasma" "fantasma.$stamp@ipt.cl" $careerAId) | Out-Null

Invoke-Req "Obtener un estudiante inexistente" 404 GET "/api/students/999999" $AdminToken $null | Out-Null

# ---------------------------------------------------------------
# CAMBIO DE ESTADO
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== CAMBIO DE ESTADO ==========================================" -ForegroundColor Cyan

$postergado = Invoke-Req "Pasar a POSTERGACION" 200 PATCH "/api/students/$studentId/status?status=POSTERGACION" $AdminToken $null
Assert-True ($postergado -and $postergado.status -eq "POSTERGACION" -and $postergado.fullName -eq "Pedro Pablo Soto" -and $postergado.studyPlanId -eq $planAId) `
    "El cambio de estado no altero los demas datos" "status '$($postergado.status)', nombre '$($postergado.fullName)'"

$postergados = Invoke-Req "Listar estudiantes POSTERGACION" 200 GET "/api/students?status=POSTERGACION" $AdminToken $null
Write-Host "          estudiantes POSTERGACION: $(@($postergados).Count)" -ForegroundColor DarkGray

Invoke-Req "Pasar a RETIRO_TEMPORAL" 200 PATCH "/api/students/$studentId/status?status=RETIRO_TEMPORAL" $AdminToken $null | Out-Null
Invoke-Req "Volver a REGULAR (en minusculas)" 200 PATCH "/api/students/$studentId/status?status=regular" $AdminToken $null | Out-Null

Invoke-Req "EGRESADO no se asigna a mano" 409 PATCH "/api/students/$studentId/status?status=EGRESADO" $AdminToken $null | Out-Null
Invoke-Req "ELIMINADO no se asigna a mano" 409 PATCH "/api/students/$studentId/status?status=ELIMINADO" $AdminToken $null | Out-Null
Invoke-Req "Estado invalido" 409 PATCH "/api/students/$studentId/status?status=CONGELADO" $AdminToken $null | Out-Null
Invoke-Req "Filtrar por un estado invalido" 409 GET "/api/students?status=CONGELADO" $AdminToken $null | Out-Null
Invoke-Req "Cambiar el estado de un estudiante inexistente" 404 PATCH "/api/students/999999/status?status=POSTERGACION" $AdminToken $null | Out-Null

# ---------------------------------------------------------------
# INTEGRIDAD REFERENCIAL
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== INTEGRIDAD REFERENCIAL ====================================" -ForegroundColor Cyan

Invoke-Req "No se borra una carrera con estudiantes" 409 DELETE "/api/careers/$careerAId" $AdminToken $null | Out-Null
Invoke-Req "No se borra un plan con estudiantes" 409 DELETE "/api/study-plans/$planAId" $AdminToken $null | Out-Null

# ---------------------------------------------------------------
# SEGURIDAD
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== SEGURIDAD =================================================" -ForegroundColor Cyan

$meId = $null
if ($StudentToken) {
    Invoke-Req "STUDENT no puede listar estudiantes" 403 GET "/api/students" $StudentToken $null | Out-Null

    Invoke-Req "STUDENT no puede registrar estudiantes" 403 POST "/api/students" $StudentToken `
        (New-StudentBody $run3 "No Permitido" "no.permitido.$stamp@ipt.cl" $careerAId) | Out-Null

    Invoke-Req "STUDENT no puede cambiar estados" 403 PATCH "/api/students/$studentId/status?status=POSTERGACION" $StudentToken $null | Out-Null

    Invoke-Req "STUDENT no puede borrar estudiantes" 403 DELETE "/api/students/$studentId" $StudentToken $null | Out-Null

    $myEmail = Get-JwtEmail -Token $StudentToken
    if ($myEmail) {
        Write-Host "          email del token STUDENT: $myEmail" -ForegroundColor DarkGray

        Invoke-Req "GET /me antes de existir el estudiante" 404 GET "/api/students/me" $StudentToken $null | Out-Null

        $me = Invoke-Req "Registrar al estudiante del token" 201 POST "/api/students" $AdminToken `
            (New-StudentBody $run4 "Estudiante Del Token" $myEmail $careerAId)
        $meId = $me.id

        $meSelf = Invoke-Req "STUDENT consulta su propia informacion" 200 GET "/api/students/me" $StudentToken $null
        Assert-True ($meSelf -and $meSelf.id -eq $meId) `
            "GET /me devolvio el estudiante del token" "llego id '$($meSelf.id)', se esperaba $meId"
    } else {
        Write-Host "  [SKIP]        pruebas de GET /me (el token no trae claim email)" -ForegroundColor Yellow
    }
} else {
    Write-Host "  [SKIP]        pruebas de rol STUDENT (sin token)" -ForegroundColor Yellow
}

Invoke-Req "Sin token" 401 GET "/api/students" "" $null | Out-Null

# ---------------------------------------------------------------
# BORRADO Y LIMPIEZA
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== BORRADO Y LIMPIEZA ========================================" -ForegroundColor Cyan

Invoke-Req "ADMIN borra el segundo estudiante" 204 DELETE "/api/students/$student2Id" $AdminToken $null | Out-Null
Invoke-Req "Borrar el mismo estudiante otra vez" 404 DELETE "/api/students/$student2Id" $AdminToken $null | Out-Null
Invoke-Req "ADMIN borra el primer estudiante" 204 DELETE "/api/students/$studentId" $AdminToken $null | Out-Null
if ($meId) {
    Invoke-Req "ADMIN borra el estudiante del token" 204 DELETE "/api/students/$meId" $AdminToken $null | Out-Null
}

# Los planes y carreras recien ahora quedan sin estudiantes asociados.
Invoke-Req "Borrar el plan 2026.1 de la carrera A" 204 DELETE "/api/study-plans/$planAId" $AdminToken $null | Out-Null
Invoke-Req "Borrar el plan 2026.2 de la carrera A" 204 DELETE "/api/study-plans/$planA2Id" $AdminToken $null | Out-Null
Invoke-Req "Borrar la carrera A" 204 DELETE "/api/careers/$careerAId" $AdminToken $null | Out-Null
Invoke-Req "Borrar la carrera B" 204 DELETE "/api/careers/$careerBId" $AdminToken $null | Out-Null
Invoke-Req "Borrar el plan de la carrera C" 204 DELETE "/api/study-plans/$planCId" $AdminToken $null | Out-Null
Invoke-Req "Borrar la carrera C" 204 DELETE "/api/careers/$careerCId" $AdminToken $null | Out-Null
Invoke-Req "Borrar el plan de la carrera D" 204 DELETE "/api/study-plans/$planDId" $AdminToken $null | Out-Null
Invoke-Req "Borrar la carrera D" 204 DELETE "/api/careers/$careerDId" $AdminToken $null | Out-Null

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
Write-Host "Nota: 'no borrar un estudiante con inscripciones o historial' y 'no cambiar" -ForegroundColor DarkGray
Write-Host "la carrera si ya tiene inscripciones' quedan pendientes hasta las Epicas 6 y 7." -ForegroundColor DarkGray
Write-Host "EGRESADO y ELIMINADO se asignaran en el cierre de periodo (Epica 7)." -ForegroundColor DarkGray
Write-Host ""

if ($script:fail -gt 0) { exit 1 }
exit 0
