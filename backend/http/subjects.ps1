# SIGA IPT - Prueba manual de Epica 3: Asignaturas
#
# Cubre las reglas de /api/subjects (codigo unico por plan, semestre, SCT, TEL,
# traslado de plan y borrado). Los prerrequisitos en si se prueban en
# http\prerequisites.ps1; aqui solo se usan para las reglas de semestre y traslado.
#
# El script crea su propia carrera y sus planes de estudio, y al final limpia todo.
#
# Uso desde cmd (login automatico contra Keycloak):
#   set KC_CLIENT_ID=tu-client-id
#   set KC_ADMIN_USER=tu-usuario-admin
#   set KC_ADMIN_PASS=tu-password
#   set KC_STUDENT_USER=tu-usuario-student
#   set KC_STUDENT_PASS=tu-password
#   powershell -ExecutionPolicy Bypass -File http\subjects.ps1
#
# Uso desde cmd (tokens a mano):
#   set ADMIN_TOKEN=eyJ...
#   set STUDENT_TOKEN=eyJ...
#   powershell -ExecutionPolicy Bypass -File http\subjects.ps1

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

# Codigos unicos por corrida.
$stamp      = Get-Date -Format "HHmmss"
$short      = $stamp.Substring(2, 4)          # mmss, para el codigo de carrera de 5 digitos
$careerCode = "5" + $short
$code1      = "TAP101-$stamp"
$code2      = "TAP201-$stamp"

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

function New-SubjectBody {
    param(
        [string]$Code,
        [string]$Name,
        [int]$Semester,
        [long]$PlanId,
        [int]$Theory    = 2,
        [int]$Exercises = 2,
        [int]$Lab       = 2,
        [int]$Sct       = 5
    )
    return @{
        code         = $Code
        name         = $Name
        semester     = $Semester
        telTheory    = $Theory
        telExercises = $Exercises
        telLab       = $Lab
        sct          = $Sct
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
    Write-Host "AVISO: sin token de STUDENT, las pruebas de rol se van a saltar." -ForegroundColor Yellow
}

# ---------------------------------------------------------------
# PREPARACION
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== PREPARACION ===============================================" -ForegroundColor Cyan

$career = Invoke-Req "Crear carrera $careerCode" 201 POST "/api/careers" $AdminToken @{
    code        = $careerCode
    name        = "Carrera subjects.ps1 $stamp"
    description = "Carrera creada por subjects.ps1"
    status      = "ACTIVA"
}
if (-not $career) { Write-Host "No se pudo crear la carrera, se aborta." -ForegroundColor Red; exit 1 }
$careerId = $career.id

$plan1 = Invoke-Req "Crear plan VIGENTE" 201 POST "/api/study-plans" $AdminToken @{
    identifier = "P1-$stamp"
    name       = "Plan principal"
    status     = "VIGENTE"
    careerId   = $careerId
}
if (-not $plan1) { Write-Host "No se pudo crear el plan, se aborta." -ForegroundColor Red; exit 1 }
$plan1Id = $plan1.id

$plan2 = Invoke-Req "Crear un segundo plan NO_VIGENTE" 201 POST "/api/study-plans" $AdminToken @{
    identifier = "P2-$stamp"
    name       = "Plan antiguo"
    status     = "NO_VIGENTE"
    careerId   = $careerId
}
$plan2Id = $plan2.id

# ---------------------------------------------------------------
# ALTA Y LECTURAS
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== ALTA Y LECTURAS ===========================================" -ForegroundColor Cyan

$s1 = Invoke-Req "Crear $code1 (semestre 1)" 201 POST "/api/subjects" $AdminToken `
    (New-SubjectBody $code1 "Programacion I" 1 $plan1Id)
if (-not $s1) { Write-Host "No se pudo crear la asignatura, se aborta." -ForegroundColor Red; exit 1 }
$s1Id = $s1.id

Assert-True ($s1.semester -eq 1 -and $s1.studyPlanId -eq $plan1Id -and $s1.sct -eq 5) `
    "La asignatura quedo en el semestre y plan indicados" "semestre '$($s1.semester)', plan '$($s1.studyPlanId)', sct '$($s1.sct)'"

$s2 = Invoke-Req "Crear $code2 (semestre 2)" 201 POST "/api/subjects" $AdminToken `
    (New-SubjectBody $code2 "Programacion II" 2 $plan1Id)
$s2Id = $s2.id

Invoke-Req "Obtener la asignatura por id" 200 GET "/api/subjects/$s1Id" $AdminToken $null | Out-Null
Invoke-Req "Listar todas las asignaturas" 200 GET "/api/subjects" $AdminToken $null | Out-Null

$delPlan = Invoke-Req "Listar las asignaturas del plan" 200 GET "/api/subjects/study-plan/$plan1Id" $AdminToken $null
Assert-True (@($delPlan).Count -eq 2) "El plan tiene las 2 asignaturas creadas" "llegaron $(@($delPlan).Count)"

Invoke-Req "Listar las asignaturas de un plan inexistente" 404 GET "/api/subjects/study-plan/999999" $AdminToken $null | Out-Null
Invoke-Req "Obtener una asignatura inexistente" 404 GET "/api/subjects/999999" $AdminToken $null | Out-Null

# ---------------------------------------------------------------
# CODIGO UNICO POR PLAN
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== CODIGO UNICO POR PLAN =====================================" -ForegroundColor Cyan

Invoke-Req "Codigo repetido en el mismo plan" 409 POST "/api/subjects" $AdminToken `
    (New-SubjectBody $code1 "Otra asignatura" 3 $plan1Id) | Out-Null

$s3 = Invoke-Req "El mismo codigo si vale en otro plan" 201 POST "/api/subjects" $AdminToken `
    (New-SubjectBody $code1 "Programacion I (plan antiguo)" 1 $plan2Id)
$s3Id = $s3.id

Invoke-Req "Cambiar el codigo al de otra asignatura del plan" 409 PUT "/api/subjects/$s2Id" $AdminToken `
    (New-SubjectBody $code1 "Programacion II" 2 $plan1Id) | Out-Null

# ---------------------------------------------------------------
# VALIDACIONES DE CAMPOS (400)
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== VALIDACIONES DE CAMPOS ====================================" -ForegroundColor Cyan

Invoke-Req "Semestre 0" 400 POST "/api/subjects" $AdminToken `
    (New-SubjectBody "SEM0-$stamp" "Semestre invalido" 0 $plan1Id) | Out-Null

Invoke-Req "Semestre 5" 400 POST "/api/subjects" $AdminToken `
    (New-SubjectBody "SEM5-$stamp" "Semestre invalido" 5 $plan1Id) | Out-Null

Invoke-Req "SCT 0" 400 POST "/api/subjects" $AdminToken `
    (New-SubjectBody "SCT0-$stamp" "SCT invalido" 1 $plan1Id 2 2 2 0) | Out-Null

Invoke-Req "SCT 8" 400 POST "/api/subjects" $AdminToken `
    (New-SubjectBody "SCT8-$stamp" "SCT invalido" 1 $plan1Id 2 2 2 8) | Out-Null

Invoke-Req "Suma TEL igual a 8" 400 POST "/api/subjects" $AdminToken `
    (New-SubjectBody "TEL8-$stamp" "TEL invalido" 1 $plan1Id 3 3 2 5) | Out-Null

Invoke-Req "Suma TEL de 7 (el limite permitido)" 201 POST "/api/subjects" $AdminToken `
    (New-SubjectBody "TEL7-$stamp" "TEL en el limite" 1 $plan1Id 3 2 2 5) | Out-Null

Invoke-Req "Horas TEL negativas" 400 POST "/api/subjects" $AdminToken `
    (New-SubjectBody "NEG-$stamp" "TEL negativo" 1 $plan1Id -1 2 2 5) | Out-Null

Invoke-Req "Plan de estudios inexistente" 404 POST "/api/subjects" $AdminToken `
    (New-SubjectBody "NOPLAN-$stamp" "Sin plan" 1 999999) | Out-Null

Invoke-Req "Body vacio" 400 POST "/api/subjects" $AdminToken @{} | Out-Null

# La asignatura del limite TEL se borra aqui para no estorbar en los conteos siguientes.
$telLimite = (Invoke-Req "Releer el plan para ubicar TEL7" 200 GET "/api/subjects/study-plan/$plan1Id" $AdminToken $null) |
    Where-Object { $_.code -eq "TEL7-$stamp" }
if ($telLimite) {
    Invoke-Req "Borrar la asignatura del limite TEL" 204 DELETE "/api/subjects/$($telLimite.id)" $AdminToken $null | Out-Null
}

# ---------------------------------------------------------------
# ACTUALIZACION
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== ACTUALIZACION =============================================" -ForegroundColor Cyan

$updated = Invoke-Req "Actualizar nombre y SCT" 200 PUT "/api/subjects/$s1Id" $AdminToken `
    (New-SubjectBody $code1 "Programacion I Avanzada" 1 $plan1Id 2 2 2 6)

Assert-True ($updated -and $updated.name -eq "Programacion I Avanzada" -and $updated.sct -eq 6 -and $updated.semester -eq 1) `
    "El PUT dejo la asignatura en el mismo plan y semestre" "nombre '$($updated.name)', sct '$($updated.sct)', semestre '$($updated.semester)'"

Invoke-Req "Actualizar una asignatura inexistente" 404 PUT "/api/subjects/999999" $AdminToken `
    (New-SubjectBody "GHOST-$stamp" "Fantasma" 1 $plan1Id) | Out-Null

Invoke-Req "Actualizar hacia un plan inexistente" 404 PUT "/api/subjects/$s1Id" $AdminToken `
    (New-SubjectBody $code1 "Programacion I Avanzada" 1 999999) | Out-Null

# ---------------------------------------------------------------
# SEMESTRES Y TRASLADO CON PRERREQUISITOS
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== SEMESTRES Y TRASLADO ======================================" -ForegroundColor Cyan

Invoke-Req "$code1 (sem 1) como prerrequisito de $code2 (sem 2)" 201 POST `
    "/api/subjects/$s2Id/prerequisites" $AdminToken @{ prerequisiteSubjectId = $s1Id } | Out-Null

Invoke-Req "Bajar la asignatura al semestre de su prerrequisito" 409 PUT "/api/subjects/$s2Id" $AdminToken `
    (New-SubjectBody $code2 "Programacion II" 1 $plan1Id) | Out-Null

Invoke-Req "Subir el prerrequisito al semestre de la asignatura" 409 PUT "/api/subjects/$s1Id" $AdminToken `
    (New-SubjectBody $code1 "Programacion I Avanzada" 2 $plan1Id 2 2 2 6) | Out-Null

Invoke-Req "Trasladar de plan una asignatura con prerrequisitos" 409 PUT "/api/subjects/$s2Id" $AdminToken `
    (New-SubjectBody $code2 "Programacion II" 2 $plan2Id) | Out-Null

Invoke-Req "Trasladar a un plan donde el codigo ya existe" 409 PUT "/api/subjects/$s1Id" $AdminToken `
    (New-SubjectBody $code1 "Programacion I Avanzada" 1 $plan2Id 2 2 2 6) | Out-Null

Invoke-Req "Borrar una asignatura que es prerrequisito de otra" 409 DELETE "/api/subjects/$s1Id" $AdminToken $null | Out-Null

Invoke-Req "Quitar el prerrequisito" 204 DELETE "/api/subjects/$s2Id/prerequisites/$s1Id" $AdminToken $null | Out-Null

Invoke-Req "Ya sin prerrequisitos, bajar al semestre 1" 200 PUT "/api/subjects/$s2Id" $AdminToken `
    (New-SubjectBody $code2 "Programacion II" 1 $plan1Id) | Out-Null

Invoke-Req "Devolver la asignatura al semestre 2" 200 PUT "/api/subjects/$s2Id" $AdminToken `
    (New-SubjectBody $code2 "Programacion II" 2 $plan1Id) | Out-Null

Invoke-Req "Liberar el codigo en el plan antiguo" 204 DELETE "/api/subjects/$s3Id" $AdminToken $null | Out-Null

$movida = Invoke-Req "Trasladar la asignatura al plan antiguo" 200 PUT "/api/subjects/$s1Id" $AdminToken `
    (New-SubjectBody $code1 "Programacion I Avanzada" 1 $plan2Id 2 2 2 6)
Assert-True ($movida -and $movida.studyPlanId -eq $plan2Id) `
    "El traslado dejo la asignatura en el plan destino" "quedo en el plan '$($movida.studyPlanId)'"

Invoke-Req "Devolver la asignatura al plan principal" 200 PUT "/api/subjects/$s1Id" $AdminToken `
    (New-SubjectBody $code1 "Programacion I Avanzada" 1 $plan1Id 2 2 2 6) | Out-Null

# ---------------------------------------------------------------
# SEGURIDAD
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== SEGURIDAD =================================================" -ForegroundColor Cyan

if ($StudentToken) {
    Invoke-Req "STUDENT puede leer asignaturas" 200 GET "/api/subjects" $StudentToken $null | Out-Null
    Invoke-Req "STUDENT puede leer el plan de estudios" 200 GET "/api/subjects/study-plan/$plan1Id" $StudentToken $null | Out-Null

    Invoke-Req "STUDENT no puede crear asignaturas" 403 POST "/api/subjects" $StudentToken `
        (New-SubjectBody "NOPERM-$stamp" "No permitido" 1 $plan1Id) | Out-Null

    Invoke-Req "STUDENT no puede actualizar asignaturas" 403 PUT "/api/subjects/$s1Id" $StudentToken `
        (New-SubjectBody $code1 "Hackeada" 1 $plan1Id) | Out-Null

    Invoke-Req "STUDENT no puede borrar asignaturas" 403 DELETE "/api/subjects/$s1Id" $StudentToken $null | Out-Null
} else {
    Write-Host "  [SKIP]        5 pruebas de rol STUDENT (sin token)" -ForegroundColor Yellow
}

Invoke-Req "Sin token" 401 GET "/api/subjects" "" $null | Out-Null

# ---------------------------------------------------------------
# BORRADO Y LIMPIEZA
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== BORRADO Y LIMPIEZA ========================================" -ForegroundColor Cyan

Invoke-Req "Borrar la segunda asignatura" 204 DELETE "/api/subjects/$s2Id" $AdminToken $null | Out-Null
Invoke-Req "Borrar la misma asignatura otra vez" 404 DELETE "/api/subjects/$s2Id" $AdminToken $null | Out-Null
Invoke-Req "Borrar la primera asignatura" 204 DELETE "/api/subjects/$s1Id" $AdminToken $null | Out-Null

Invoke-Req "Borrar el plan principal" 204 DELETE "/api/study-plans/$plan1Id" $AdminToken $null | Out-Null
Invoke-Req "Borrar el plan antiguo" 204 DELETE "/api/study-plans/$plan2Id" $AdminToken $null | Out-Null
Invoke-Req "Borrar la carrera" 204 DELETE "/api/careers/$careerId" $AdminToken $null | Out-Null

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
Write-Host "Nota: 'no eliminar una asignatura que esta en el historial academico'" -ForegroundColor DarkGray
Write-Host "queda pendiente hasta la Epica 7 (AcademicHistory)." -ForegroundColor DarkGray
Write-Host ""

if ($script:fail -gt 0) { exit 1 }
exit 0
