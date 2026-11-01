# SIGA IPT - Prueba manual de Epica 5: Oferta academica, periodos y secciones
#
# Cubre /api/academic-periods y /api/sections: periodo ABIERTO/CERRADO sin retorno,
# una seccion por asignatura y periodo, docente ACTIVO, capacidad, bloques = TEL/2,
# bloques repetidos, choque de horario del docente y las reglas de borrado.
#
# El script crea su propia carrera, plan, asignaturas, docentes y periodos, y al final
# limpia todo (salvo lo que el propio sistema impide borrar, que se informa al cierre).
#
# Uso desde cmd (login automatico contra Keycloak):
#   set KC_CLIENT_ID=tu-client-id
#   set KC_ADMIN_USER=tu-usuario-admin
#   set KC_ADMIN_PASS=tu-password
#   set KC_STUDENT_USER=tu-usuario-student
#   set KC_STUDENT_PASS=tu-password
#   powershell -ExecutionPolicy Bypass -File http\sections.ps1
#
# Uso desde cmd (tokens a mano):
#   set ADMIN_TOKEN=eyJ...
#   set STUDENT_TOKEN=eyJ...
#   powershell -ExecutionPolicy Bypass -File http\sections.ps1

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

# Identificadores unicos por corrida.
$stamp      = Get-Date -Format "HHmmss"
$short      = $stamp.Substring(2, 4)
$careerCode = "4" + $short
$run1       = ("1" + $stamp + "1").Substring(0, 8) + "-9"
$run2       = ("2" + $stamp + "2").Substring(0, 8) + "-7"
# Anios altos para no chocar con periodos reales que ya existan en la base.
$yearA      = 2090
$yearB      = 2091

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
        $reqArgs["Body"]        = ($Body | ConvertTo-Json -Compress -Depth 5)
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
    param([string]$Code, [string]$Name, [int]$Semester, [long]$PlanId,
          [int]$Theory = 2, [int]$Exercises = 2, [int]$Lab = 2, [int]$Sct = 5)
    return @{
        code = $Code; name = $Name; semester = $Semester
        telTheory = $Theory; telExercises = $Exercises; telLab = $Lab
        sct = $Sct; studyPlanId = $PlanId
    }
}

function New-TeacherBody {
    param([string]$Run, [string]$Name, [string]$Email)
    return @{
        run = $Run; fullName = $Name; email = $Email
        professionalTitle = "Ingeniero en Informatica"; academicDegree = "Magister"
    }
}

function New-Block {
    param([string]$Day, [int]$Module)
    return @{ day = $Day; module = $Module }
}

function New-SectionBody {
    param([long]$SubjectId, [long]$PeriodId, [long]$TeacherId, [int]$Capacity, $Blocks)
    return @{
        subjectId        = $SubjectId
        academicPeriodId = $PeriodId
        teacherId        = $TeacherId
        capacity         = $Capacity
        scheduleBlocks   = @($Blocks)
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
    code = $careerCode; name = "Carrera sections.ps1 $stamp"
    description = "Carrera creada por sections.ps1"; status = "ACTIVA"
}
if (-not $career) { Write-Host "No se pudo crear la carrera, se aborta." -ForegroundColor Red; exit 1 }
$careerId = $career.id

$plan = Invoke-Req "Crear plan VIGENTE" 201 POST "/api/study-plans" $AdminToken @{
    identifier = "P1-$stamp"; name = "Plan oferta"; status = "VIGENTE"; careerId = $careerId
}
if (-not $plan) { Write-Host "No se pudo crear el plan, se aborta." -ForegroundColor Red; exit 1 }
$planId = $plan.id

# TEL 2+2+2 = 6 horas -> 3 bloques exactos
$subjA = Invoke-Req "Crear asignatura A (TEL 6 -> 3 bloques)" 201 POST "/api/subjects" $AdminToken `
    (New-SubjectBody "OFA101-$stamp" "Asignatura A" 1 $planId 2 2 2 5)
$subjAId = $subjA.id

# TEL 2+2+0 = 4 horas -> 2 bloques exactos
$subjB = Invoke-Req "Crear asignatura B (TEL 4 -> 2 bloques)" 201 POST "/api/subjects" $AdminToken `
    (New-SubjectBody "OFA102-$stamp" "Asignatura B" 1 $planId 2 2 0 4)
$subjBId = $subjB.id

$subjC = Invoke-Req "Crear asignatura C (TEL 4 -> 2 bloques)" 201 POST "/api/subjects" $AdminToken `
    (New-SubjectBody "OFA103-$stamp" "Asignatura C" 1 $planId 2 2 0 4)
$subjCId = $subjC.id

$teacher1 = Invoke-Req "Crear docente 1" 201 POST "/api/teachers" $AdminToken `
    (New-TeacherBody $run1 "Ana Perez" "ana.oferta.$stamp@ipt.cl")
$teacher1Id = $teacher1.id

$teacher2 = Invoke-Req "Crear docente 2 (quedara INACTIVO)" 201 POST "/api/teachers" $AdminToken `
    (New-TeacherBody $run2 "Luis Soto" "luis.oferta.$stamp@ipt.cl")
$teacher2Id = $teacher2.id

Invoke-Req "Pasar al docente 2 a INACTIVO" 200 PATCH "/api/teachers/$teacher2Id/status?status=INACTIVO" $AdminToken $null | Out-Null

# ---------------------------------------------------------------
# PERIODOS ACADEMICOS
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== PERIODOS ACADEMICOS =======================================" -ForegroundColor Cyan

$periodA = Invoke-Req "Crear periodo $yearA-1" 201 POST "/api/academic-periods" $AdminToken @{
    year = $yearA; semester = 1
}
if (-not $periodA) { Write-Host "No se pudo crear el periodo, se aborta." -ForegroundColor Red; exit 1 }
$periodAId = $periodA.id

Assert-True ($periodA.code -eq "$yearA-1" -and $periodA.status -eq "ABIERTO") `
    "El periodo nace ABIERTO y con codigo $yearA-1" "llego code '$($periodA.code)' y status '$($periodA.status)'"

Invoke-Req "Mismo anio y semestre repetido" 409 POST "/api/academic-periods" $AdminToken @{
    year = $yearA; semester = 1
} | Out-Null

$periodB = Invoke-Req "Crear periodo $yearB-2 (se cerrara)" 201 POST "/api/academic-periods" $AdminToken @{
    year = $yearB; semester = 2
}
$periodBId = $periodB.id

Invoke-Req "Semestre 3 invalido" 400 POST "/api/academic-periods" $AdminToken @{ year = $yearA; semester = 3 } | Out-Null
Invoke-Req "Anio 1999 invalido" 400 POST "/api/academic-periods" $AdminToken @{ year = 1999; semester = 1 } | Out-Null
Invoke-Req "Body vacio" 400 POST "/api/academic-periods" $AdminToken @{} | Out-Null

Invoke-Req "Obtener el periodo por id" 200 GET "/api/academic-periods/$periodAId" $AdminToken $null | Out-Null
Invoke-Req "Listar periodos" 200 GET "/api/academic-periods" $AdminToken $null | Out-Null
Invoke-Req "Listar periodos ABIERTO" 200 GET "/api/academic-periods?status=ABIERTO" $AdminToken $null | Out-Null
Invoke-Req "Filtrar por un estado invalido" 409 GET "/api/academic-periods?status=EN_CURSO" $AdminToken $null | Out-Null
Invoke-Req "Obtener un periodo inexistente" 404 GET "/api/academic-periods/999999" $AdminToken $null | Out-Null

# ---------------------------------------------------------------
# SECCIONES: CASOS FELICES
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== SECCIONES: CASOS FELICES ==================================" -ForegroundColor Cyan

$sectionA = Invoke-Req "Crear seccion de A con 3 bloques" 201 POST "/api/sections" $AdminToken `
    (New-SectionBody $subjAId $periodAId $teacher1Id 30 @((New-Block "L" 1), (New-Block "L" 2), (New-Block "M" 1)))
if (-not $sectionA) { Write-Host "No se pudo crear la seccion, se aborta." -ForegroundColor Red; exit 1 }
$sectionAId = $sectionA.id

Assert-True (@($sectionA.scheduleBlocks).Count -eq 3 -and $sectionA.capacity -eq 30 -and $sectionA.teacherId -eq $teacher1Id) `
    "La seccion quedo con 3 bloques, capacidad 30 y su docente" "bloques $(@($sectionA.scheduleBlocks).Count), capacidad $($sectionA.capacity)"

$sectionB = Invoke-Req "Crear seccion de B con 2 bloques, sin choque" 201 POST "/api/sections" $AdminToken `
    (New-SectionBody $subjBId $periodAId $teacher1Id 20 @((New-Block "W" 3), (New-Block "J" 4)))
$sectionBId = $sectionB.id

Invoke-Req "Obtener la seccion por id" 200 GET "/api/sections/$sectionAId" $AdminToken $null | Out-Null
Invoke-Req "Listar todas las secciones" 200 GET "/api/sections" $AdminToken $null | Out-Null

$delPeriodo = Invoke-Req "Listar la oferta del periodo" 200 GET "/api/sections?academicPeriodId=$periodAId" $AdminToken $null
Assert-True (@($delPeriodo).Count -eq 2) "El periodo tiene las 2 secciones creadas" "llegaron $(@($delPeriodo).Count)"

$delDocente = Invoke-Req "Listar las secciones del docente" 200 GET "/api/sections?teacherId=$teacher1Id" $AdminToken $null
Assert-True (@($delDocente).Count -eq 2) "El docente 1 tiene 2 secciones" "llegaron $(@($delDocente).Count)"

Invoke-Req "Listar la oferta de un periodo inexistente" 404 GET "/api/sections?academicPeriodId=999999" $AdminToken $null | Out-Null
Invoke-Req "Listar las secciones de un docente inexistente" 404 GET "/api/sections?teacherId=999999" $AdminToken $null | Out-Null

# ---------------------------------------------------------------
# SECCIONES: CASOS DE RECHAZO
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== SECCIONES: CASOS DE RECHAZO ===============================" -ForegroundColor Cyan

Invoke-Req "Dos secciones para la misma asignatura y periodo" 409 POST "/api/sections" $AdminToken `
    (New-SectionBody $subjAId $periodAId $teacher1Id 30 @((New-Block "V" 1), (New-Block "V" 2), (New-Block "V" 3))) | Out-Null

Invoke-Req "Docente INACTIVO" 409 POST "/api/sections" $AdminToken `
    (New-SectionBody $subjCId $periodAId $teacher2Id 30 @((New-Block "V" 1), (New-Block "V" 2))) | Out-Null

Invoke-Req "Cantidad de bloques distinta de TEL/2" 409 POST "/api/sections" $AdminToken `
    (New-SectionBody $subjCId $periodAId $teacher1Id 30 @((New-Block "V" 1))) | Out-Null

Invoke-Req "Bloques repetidos" 409 POST "/api/sections" $AdminToken `
    (New-SectionBody $subjCId $periodAId $teacher1Id 30 @((New-Block "V" 5), (New-Block "V" 5))) | Out-Null

Invoke-Req "Choque de horario del docente" 409 POST "/api/sections" $AdminToken `
    (New-SectionBody $subjCId $periodAId $teacher1Id 30 @((New-Block "L" 1), (New-Block "V" 6))) | Out-Null

Invoke-Req "Capacidad 0" 400 POST "/api/sections" $AdminToken `
    (New-SectionBody $subjCId $periodAId $teacher1Id 0 @((New-Block "V" 1), (New-Block "V" 2))) | Out-Null

Invoke-Req "Capacidad 61" 400 POST "/api/sections" $AdminToken `
    (New-SectionBody $subjCId $periodAId $teacher1Id 61 @((New-Block "V" 1), (New-Block "V" 2))) | Out-Null

Invoke-Req "Dia invalido" 400 POST "/api/sections" $AdminToken `
    (New-SectionBody $subjCId $periodAId $teacher1Id 30 @((New-Block "X" 1), (New-Block "V" 2))) | Out-Null

Invoke-Req "Modulo 7" 400 POST "/api/sections" $AdminToken `
    (New-SectionBody $subjCId $periodAId $teacher1Id 30 @((New-Block "V" 7), (New-Block "V" 2))) | Out-Null

Invoke-Req "Sin bloques horarios" 400 POST "/api/sections" $AdminToken `
    (New-SectionBody $subjCId $periodAId $teacher1Id 30 @()) | Out-Null

Invoke-Req "Asignatura inexistente" 404 POST "/api/sections" $AdminToken `
    (New-SectionBody 999999 $periodAId $teacher1Id 30 @((New-Block "V" 1), (New-Block "V" 2))) | Out-Null

Invoke-Req "Periodo inexistente" 404 POST "/api/sections" $AdminToken `
    (New-SectionBody $subjCId 999999 $teacher1Id 30 @((New-Block "V" 1), (New-Block "V" 2))) | Out-Null

Invoke-Req "Docente inexistente" 404 POST "/api/sections" $AdminToken `
    (New-SectionBody $subjCId $periodAId 999999 30 @((New-Block "V" 1), (New-Block "V" 2))) | Out-Null

# ---------------------------------------------------------------
# ACTUALIZACION Y HORARIO
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== ACTUALIZACION Y HORARIO ===================================" -ForegroundColor Cyan

$updated = Invoke-Req "Cambiar capacidad y horario de la seccion" 200 PUT "/api/sections/$sectionAId" $AdminToken `
    (New-SectionBody $subjAId $periodAId $teacher1Id 45 @((New-Block "L" 1), (New-Block "L" 2), (New-Block "L" 3)))
Assert-True ($updated -and $updated.capacity -eq 45 -and @($updated.scheduleBlocks).Count -eq 3) `
    "La seccion quedo con capacidad 45 y 3 bloques" "capacidad $($updated.capacity), bloques $(@($updated.scheduleBlocks).Count)"

$rehorario = Invoke-Req "Reemplazar el horario completo" 200 POST "/api/sections/$sectionAId/schedule-blocks" $AdminToken `
    @((New-Block "M" 4), (New-Block "M" 5), (New-Block "M" 6))
Assert-True ($rehorario -and @($rehorario.scheduleBlocks).Count -eq 3) `
    "El horario nuevo reemplazo al anterior" "bloques $(@($rehorario.scheduleBlocks).Count)"

Invoke-Req "Reemplazar el horario con la cantidad equivocada" 409 POST "/api/sections/$sectionAId/schedule-blocks" $AdminToken `
    @((New-Block "M" 4), (New-Block "M" 5)) | Out-Null

Invoke-Req "Reemplazar el horario con bloques repetidos" 409 POST "/api/sections/$sectionAId/schedule-blocks" $AdminToken `
    @((New-Block "M" 4), (New-Block "M" 4), (New-Block "M" 6)) | Out-Null

Invoke-Req "Reemplazar el horario chocando con la otra seccion" 409 POST "/api/sections/$sectionAId/schedule-blocks" $AdminToken `
    @((New-Block "W" 3), (New-Block "M" 5), (New-Block "M" 6)) | Out-Null

Invoke-Req "Asignar un docente INACTIVO en el PUT" 409 PUT "/api/sections/$sectionAId" $AdminToken `
    (New-SectionBody $subjAId $periodAId $teacher2Id 45 @((New-Block "M" 4), (New-Block "M" 5), (New-Block "M" 6))) | Out-Null

Invoke-Req "Actualizar una seccion inexistente" 404 PUT "/api/sections/999999" $AdminToken `
    (New-SectionBody $subjAId $periodAId $teacher1Id 30 @((New-Block "V" 1), (New-Block "V" 2), (New-Block "V" 3))) | Out-Null

Invoke-Req "Obtener una seccion inexistente" 404 GET "/api/sections/999999" $AdminToken $null | Out-Null

# ---------------------------------------------------------------
# INTEGRIDAD REFERENCIAL Y PERIODO CERRADO
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== INTEGRIDAD Y PERIODO CERRADO ==============================" -ForegroundColor Cyan

Invoke-Req "No se borra un docente con secciones" 409 DELETE "/api/teachers/$teacher1Id" $AdminToken $null | Out-Null
Invoke-Req "No se inactiva un docente con secciones en periodo ABIERTO" 409 PATCH "/api/teachers/$teacher1Id/status?status=INACTIVO" $AdminToken $null | Out-Null
Invoke-Req "No se borra una asignatura con secciones" 409 DELETE "/api/subjects/$subjAId" $AdminToken $null | Out-Null
Invoke-Req "No se borra un periodo con secciones" 409 DELETE "/api/academic-periods/$periodAId" $AdminToken $null | Out-Null

# El periodo B esta vacio: se cierra para probar las reglas del CERRADO.
$cerrado = Invoke-Req "Cerrar el periodo $yearB-2" 200 PATCH "/api/academic-periods/$periodBId/status?status=CERRADO" $AdminToken $null
Assert-True ($cerrado -and $cerrado.status -eq "CERRADO") "El periodo quedo CERRADO" "llego status '$($cerrado.status)'"

Invoke-Req "Un periodo CERRADO no se reabre" 409 PATCH "/api/academic-periods/$periodBId/status?status=ABIERTO" $AdminToken $null | Out-Null
Invoke-Req "Un periodo CERRADO no se vuelve a cerrar" 409 PATCH "/api/academic-periods/$periodBId/status?status=CERRADO" $AdminToken $null | Out-Null
Invoke-Req "Un periodo CERRADO no se modifica" 409 PUT "/api/academic-periods/$periodBId" $AdminToken @{ year = $yearB; semester = 1 } | Out-Null
Invoke-Req "Un periodo CERRADO no se borra" 409 DELETE "/api/academic-periods/$periodBId" $AdminToken $null | Out-Null
Invoke-Req "No se arma oferta en un periodo CERRADO" 409 POST "/api/sections" $AdminToken `
    (New-SectionBody $subjCId $periodBId $teacher1Id 30 @((New-Block "V" 1), (New-Block "V" 2))) | Out-Null
Invoke-Req "Estado de periodo invalido" 409 PATCH "/api/academic-periods/$periodAId/status?status=SUSPENDIDO" $AdminToken $null | Out-Null
Invoke-Req "Cerrar un periodo inexistente" 404 PATCH "/api/academic-periods/999999/status?status=CERRADO" $AdminToken $null | Out-Null

# ---------------------------------------------------------------
# SEGURIDAD
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== SEGURIDAD =================================================" -ForegroundColor Cyan

if ($StudentToken) {
    Invoke-Req "STUDENT puede leer la oferta" 200 GET "/api/sections" $StudentToken $null | Out-Null
    Invoke-Req "STUDENT puede leer los periodos" 200 GET "/api/academic-periods" $StudentToken $null | Out-Null

    Invoke-Req "STUDENT no puede crear secciones" 403 POST "/api/sections" $StudentToken `
        (New-SectionBody $subjCId $periodAId $teacher1Id 30 @((New-Block "V" 1), (New-Block "V" 2))) | Out-Null

    Invoke-Req "STUDENT no puede cambiar el horario" 403 POST "/api/sections/$sectionAId/schedule-blocks" $StudentToken `
        @((New-Block "V" 1), (New-Block "V" 2), (New-Block "V" 3)) | Out-Null

    Invoke-Req "STUDENT no puede crear periodos" 403 POST "/api/academic-periods" $StudentToken @{ year = 2092; semester = 1 } | Out-Null
    Invoke-Req "STUDENT no puede cerrar periodos" 403 PATCH "/api/academic-periods/$periodAId/status?status=CERRADO" $StudentToken $null | Out-Null
    Invoke-Req "STUDENT no puede borrar secciones" 403 DELETE "/api/sections/$sectionAId" $StudentToken $null | Out-Null
} else {
    Write-Host "  [SKIP]        7 pruebas de rol STUDENT (sin token)" -ForegroundColor Yellow
}

Invoke-Req "Sin token" 401 GET "/api/sections" "" $null | Out-Null

# ---------------------------------------------------------------
# BORRADO Y LIMPIEZA
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== BORRADO Y LIMPIEZA ========================================" -ForegroundColor Cyan

Invoke-Req "Borrar la seccion de B" 204 DELETE "/api/sections/$sectionBId" $AdminToken $null | Out-Null
Invoke-Req "Borrar la misma seccion otra vez" 404 DELETE "/api/sections/$sectionBId" $AdminToken $null | Out-Null
Invoke-Req "Borrar la seccion de A" 204 DELETE "/api/sections/$sectionAId" $AdminToken $null | Out-Null

Invoke-Req "Ya sin secciones, inactivar al docente 1" 200 PATCH "/api/teachers/$teacher1Id/status?status=INACTIVO" $AdminToken $null | Out-Null
Invoke-Req "Borrar el periodo $yearA-1" 204 DELETE "/api/academic-periods/$periodAId" $AdminToken $null | Out-Null

Invoke-Req "Borrar el docente 1" 204 DELETE "/api/teachers/$teacher1Id" $AdminToken $null | Out-Null
Invoke-Req "Borrar el docente 2" 204 DELETE "/api/teachers/$teacher2Id" $AdminToken $null | Out-Null

Invoke-Req "Borrar la asignatura A" 204 DELETE "/api/subjects/$subjAId" $AdminToken $null | Out-Null
Invoke-Req "Borrar la asignatura B" 204 DELETE "/api/subjects/$subjBId" $AdminToken $null | Out-Null
Invoke-Req "Borrar la asignatura C" 204 DELETE "/api/subjects/$subjCId" $AdminToken $null | Out-Null
Invoke-Req "Borrar el plan de estudios" 204 DELETE "/api/study-plans/$planId" $AdminToken $null | Out-Null
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
Write-Host "Nota: el periodo $yearB-2 queda CERRADO en la base: por regla de negocio" -ForegroundColor DarkGray
Write-Host "un periodo cerrado no se puede borrar. Es el unico residuo del script." -ForegroundColor DarkGray
Write-Host "'No eliminar una seccion con inscritos' y 'cupos' quedan pendientes" -ForegroundColor DarkGray
Write-Host "hasta la Epica 6 (Enrollment)." -ForegroundColor DarkGray
Write-Host ""

if ($script:fail -gt 0) { exit 1 }
exit 0
