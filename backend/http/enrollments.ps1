# SIGA IPT - Prueba manual de Epica 6: Inscripcion academica
#
# Cubre /api/student-period-status y /api/enrollments: estudiante REGULAR y
# matriculado, periodo ABIERTO, asignaturas del plan, prerrequisitos, minimo 3 y
# maximo 6, choque de horario, no reinscribir en el mismo periodo y anulacion.
#
# NECESITA UN TOKEN DE STUDENT: la inscripcion y la anulacion son del estudiante
# (se identifica por el email del token). Sin ese token el script solo prueba
# la parte administrativa.
#
# Uso desde cmd (login automatico contra Keycloak):
#   set KC_CLIENT_ID=tu-client-id
#   set KC_ADMIN_USER=tu-usuario-admin
#   set KC_ADMIN_PASS=tu-password
#   set KC_STUDENT_USER=tu-usuario-student
#   set KC_STUDENT_PASS=tu-password
#   powershell -ExecutionPolicy Bypass -File http\enrollments.ps1

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

$stamp      = Get-Date -Format "HHmmss"
$short      = $stamp.Substring(2, 4)
$careerCode = "3" + $short
$run1       = ("1" + $stamp + "1").Substring(0, 8) + "-9"
$teacherRun = ("7" + $stamp + "7").Substring(0, 8) + "-5"
# Periodo nuevo en cada corrida: las inscripciones viven por periodo, asi cada
# ejecucion parte con el estudiante sin nada inscrito.
$year       = 2001 + ([int]$stamp % 98)

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

# Llamada silenciosa, para preparar datos sin contarla como prueba.
function Invoke-Quiet {
    param([string]$Method, [string]$Path, [string]$Token, $Body)

    $headers = @{ Authorization = "Bearer $Token" }
    $reqArgs = @{
        Uri = "$BaseUrl$Path"; Method = $Method; Headers = $headers; UseBasicParsing = $true
    }
    if ($null -ne $Body) {
        $reqArgs["Body"]        = ($Body | ConvertTo-Json -Compress -Depth 5)
        $reqArgs["ContentType"] = "application/json"
    }
    try {
        return ((Invoke-WebRequest @reqArgs).Content | ConvertFrom-Json)
    } catch {
        return $null
    }
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

function New-Block {
    param([string]$Day, [int]$Module)
    return @{ day = $Day; module = $Module }
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

$studentEmail = $null
if ($StudentToken) {
    $studentEmail = Get-JwtEmail -Token $StudentToken
    if (-not $studentEmail) {
        Write-Host "AVISO: el token de STUDENT no trae claim email; se saltan las inscripciones." -ForegroundColor Yellow
    }
} else {
    Write-Host "AVISO: sin token de STUDENT no se pueden probar las inscripciones." -ForegroundColor Yellow
}

# ---------------------------------------------------------------
# PREPARACION
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== PREPARACION ===============================================" -ForegroundColor Cyan

$career = Invoke-Req "Crear carrera $careerCode" 201 POST "/api/careers" $AdminToken @{
    code = $careerCode; name = "Carrera enrollments.ps1 $stamp"
    description = "Carrera creada por enrollments.ps1"; status = "ACTIVA"
}
if (-not $career) { Write-Host "No se pudo crear la carrera, se aborta." -ForegroundColor Red; exit 1 }
$careerId = $career.id

$plan = Invoke-Req "Crear plan VIGENTE" 201 POST "/api/study-plans" $AdminToken @{
    identifier = "P1-$stamp"; name = "Plan inscripcion"; status = "VIGENTE"; careerId = $careerId
}
if (-not $plan) { Write-Host "No se pudo crear el plan, se aborta." -ForegroundColor Red; exit 1 }
$planId = $plan.id

$period = Invoke-Req "Crear periodo $year-1" 201 POST "/api/academic-periods" $AdminToken @{
    year = $year; semester = 1
}
if (-not $period) {
    Write-Host "No se pudo crear el periodo (quizas ya existe $year-1). Reintenta en un minuto." -ForegroundColor Red
    exit 1
}
$periodId = $period.id

$periodClosed = Invoke-Req "Crear un segundo periodo (se cerrara)" 201 POST "/api/academic-periods" $AdminToken @{
    year = $year; semester = 2
}
$periodClosedId = $periodClosed.id

$teacher = Invoke-Req "Crear docente" 201 POST "/api/teachers" $AdminToken @{
    run = $teacherRun; fullName = "Ana Perez"; email = "ana.insc.$stamp@ipt.cl"
    professionalTitle = "Ingeniera"; academicDegree = "Magister"
}
$teacherId = $teacher.id

# 7 asignaturas de semestre 1 (TEL 2+2+0 = 4 horas -> 2 bloques) con horarios que no chocan,
# 1 de semestre 2 con prerrequisito y 1 que choca con la primera.
$layout = @(
    @{ n = 1; day1 = "L"; mod1 = 1; day2 = "L"; mod2 = 2; sem = 1 },
    @{ n = 2; day1 = "M"; mod1 = 1; day2 = "M"; mod2 = 2; sem = 1 },
    @{ n = 3; day1 = "W"; mod1 = 1; day2 = "W"; mod2 = 2; sem = 1 },
    @{ n = 4; day1 = "J"; mod1 = 1; day2 = "J"; mod2 = 2; sem = 1 },
    @{ n = 5; day1 = "V"; mod1 = 1; day2 = "V"; mod2 = 2; sem = 1 },
    @{ n = 6; day1 = "L"; mod1 = 3; day2 = "L"; mod2 = 4; sem = 1 },
    @{ n = 7; day1 = "M"; mod1 = 3; day2 = "M"; mod2 = 4; sem = 1 },
    @{ n = 8; day1 = "W"; mod1 = 3; day2 = "W"; mod2 = 4; sem = 2 },
    @{ n = 9; day1 = "L"; mod1 = 1; day2 = "J"; mod2 = 6; sem = 1 }
)

$subjectIds = @{}
$sectionIds = @{}
foreach ($row in $layout) {
    $n = $row.n
    $subject = Invoke-Quiet POST "/api/subjects" $AdminToken @{
        code = "INS$n-$stamp"; name = "Asignatura $n"; semester = $row.sem
        telTheory = 2; telExercises = 2; telLab = 0; sct = 4; studyPlanId = $planId
    }
    if (-not $subject) { Write-Host "No se pudo crear la asignatura $n, se aborta." -ForegroundColor Red; exit 1 }
    $subjectIds[$n] = $subject.id

    $section = Invoke-Quiet POST "/api/sections" $AdminToken @{
        subjectId = $subject.id; academicPeriodId = $periodId; teacherId = $teacherId
        capacity = 30
        scheduleBlocks = @((New-Block $row.day1 $row.mod1), (New-Block $row.day2 $row.mod2))
    }
    if (-not $section) { Write-Host "No se pudo crear la seccion $n, se aborta." -ForegroundColor Red; exit 1 }
    $sectionIds[$n] = $section.id
}
Write-Host "  [OK]         9 asignaturas con seccion creadas en el periodo $year-1" -ForegroundColor Green
$script:pass++

# La 8 (semestre 2) exige la 1 como prerrequisito.
Invoke-Req "La asignatura 8 (sem 2) exige la 1 (sem 1)" 201 POST `
    "/api/subjects/$($subjectIds[8])/prerequisites" $AdminToken @{ prerequisiteSubjectId = $subjectIds[1] } | Out-Null

# ---------------------------------------------------------------
# ESTUDIANTE Y MATRICULA
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== ESTUDIANTE Y MATRICULA ====================================" -ForegroundColor Cyan

$studentId = $null
if ($studentEmail) {
    # El email es unico: si el estudiante ya existe de una corrida anterior, se reutiliza.
    $student = Invoke-Quiet POST "/api/students" $AdminToken @{
        run = $run1; fullName = "Estudiante Del Token"; email = $studentEmail; careerId = $careerId
    }
    if ($student) {
        Write-Host "  [OK]         Estudiante creado para el email del token" -ForegroundColor Green
        $script:pass++
        $studentId = $student.id
    } else {
        $all = Invoke-Quiet GET "/api/students" $AdminToken $null
        $existing = @($all) | Where-Object { $_.email -eq $studentEmail } | Select-Object -First 1
        if ($existing) {
            Write-Host "  [OK]         Estudiante reutilizado de una corrida anterior (id $($existing.id))" -ForegroundColor Green
            Write-Host "               ojo: su carrera y plan son los de esa corrida" -ForegroundColor DarkGray
            $script:pass++
            $studentId = $existing.id
        } else {
            Write-Host "  [FALLA]      No se pudo crear ni encontrar al estudiante del token" -ForegroundColor Red
            $script:fail++
        }
    }
}

if ($studentId) {
    Invoke-Req "Sin matricula no hay inscripcion posible" 404 GET `
        "/api/student-period-status?studentId=$studentId&academicPeriodId=$periodId" $AdminToken $null | Out-Null

    $matricula = Invoke-Req "Matricular al estudiante en el periodo" 200 PUT "/api/student-period-status" $AdminToken @{
        studentId = $studentId; academicPeriodId = $periodId; enrollmentStatus = "MATRICULATED"
    }
    Assert-True ($matricula -and $matricula.enrollmentStatus -eq "MATRICULATED") `
        "La matricula quedo MATRICULATED" "llego '$($matricula.enrollmentStatus)'"

    Invoke-Req "Volver a matricular actualiza el mismo registro" 200 PUT "/api/student-period-status" $AdminToken @{
        studentId = $studentId; academicPeriodId = $periodId; enrollmentStatus = "MATRICULATED"
    } | Out-Null

    Invoke-Req "Consultar la matricula" 200 GET `
        "/api/student-period-status?studentId=$studentId&academicPeriodId=$periodId" $AdminToken $null | Out-Null

    Invoke-Req "Listar las matriculas del periodo" 200 GET "/api/student-period-status/period/$periodId" $AdminToken $null | Out-Null

    Invoke-Req "Matricular a un estudiante inexistente" 404 PUT "/api/student-period-status" $AdminToken @{
        studentId = 999999; academicPeriodId = $periodId; enrollmentStatus = "MATRICULATED"
    } | Out-Null

    Invoke-Req "Estado de matricula invalido" 400 PUT "/api/student-period-status" $AdminToken @{
        studentId = $studentId; academicPeriodId = $periodId; enrollmentStatus = "QUIZAS"
    } | Out-Null
}

# ---------------------------------------------------------------
# OFERTA DISPONIBLE E INSCRIPCION
# ---------------------------------------------------------------

if ($studentId -and $StudentToken) {
    Write-Host ""
    Write-Host "=== OFERTA DISPONIBLE =========================================" -ForegroundColor Cyan

    $available = Invoke-Req "El estudiante consulta su oferta disponible" 200 GET `
        "/api/enrollments/available?academicPeriodId=$periodId" $StudentToken $null
    Assert-True (@($available).Count -eq 8) `
        "Aparecen las 8 de semestre 1 y no la 8 (prerrequisito pendiente)" "llegaron $(@($available).Count)"

    $conPrerrequisito = @($available) | Where-Object { $_.subjectCode -eq "INS8-$stamp" }
    Assert-True ($null -eq $conPrerrequisito) `
        "La asignatura con prerrequisito pendiente no esta disponible" "aparecio en la lista"

    Invoke-Req "ADMIN consulta la oferta del estudiante" 200 GET `
        "/api/enrollments/available?academicPeriodId=$periodId&studentId=$studentId" $AdminToken $null | Out-Null

    Write-Host ""
    Write-Host "=== INSCRIPCION ===============================================" -ForegroundColor Cyan

    Invoke-Req "Menos de 3 asignaturas" 409 POST "/api/enrollments" $StudentToken @{
        academicPeriodId = $periodId; sectionIds = @($sectionIds[1], $sectionIds[2])
    } | Out-Null

    Invoke-Req "Mas de 6 asignaturas" 409 POST "/api/enrollments" $StudentToken @{
        academicPeriodId = $periodId
        sectionIds = @($sectionIds[1], $sectionIds[2], $sectionIds[3], $sectionIds[4],
                       $sectionIds[5], $sectionIds[6], $sectionIds[7])
    } | Out-Null

    Invoke-Req "La misma seccion dos veces en la peticion" 409 POST "/api/enrollments" $StudentToken @{
        academicPeriodId = $periodId; sectionIds = @($sectionIds[1], $sectionIds[1], $sectionIds[2])
    } | Out-Null

    Invoke-Req "Choque de horario entre las seleccionadas" 409 POST "/api/enrollments" $StudentToken @{
        academicPeriodId = $periodId; sectionIds = @($sectionIds[1], $sectionIds[9], $sectionIds[2])
    } | Out-Null

    Invoke-Req "Prerrequisito no aprobado" 409 POST "/api/enrollments" $StudentToken @{
        academicPeriodId = $periodId; sectionIds = @($sectionIds[8], $sectionIds[1], $sectionIds[2])
    } | Out-Null

    Invoke-Req "Seccion inexistente" 404 POST "/api/enrollments" $StudentToken @{
        academicPeriodId = $periodId; sectionIds = @(999999, $sectionIds[1], $sectionIds[2])
    } | Out-Null

    Invoke-Req "Sin secciones" 400 POST "/api/enrollments" $StudentToken @{
        academicPeriodId = $periodId; sectionIds = @()
    } | Out-Null

    $inscritas = Invoke-Req "Inscribir 3 asignaturas validas" 201 POST "/api/enrollments" $StudentToken @{
        academicPeriodId = $periodId; sectionIds = @($sectionIds[1], $sectionIds[2], $sectionIds[3])
    }
    Assert-True (@($inscritas).Count -eq 3 -and @($inscritas)[0].status -eq "INSCRITA") `
        "Quedaron 3 inscripciones en estado INSCRITA" "llegaron $(@($inscritas).Count)"

    $primeraId = @($inscritas)[0].id

    Invoke-Req "Reinscribir la misma asignatura del periodo" 409 POST "/api/enrollments" $StudentToken @{
        academicPeriodId = $periodId; sectionIds = @($sectionIds[1])
    } | Out-Null

    Invoke-Req "Choque con una asignatura ya inscrita" 409 POST "/api/enrollments" $StudentToken @{
        academicPeriodId = $periodId; sectionIds = @($sectionIds[9])
    } | Out-Null

    Invoke-Req "Completar hasta 6 asignaturas" 201 POST "/api/enrollments" $StudentToken @{
        academicPeriodId = $periodId; sectionIds = @($sectionIds[4], $sectionIds[5], $sectionIds[6])
    } | Out-Null

    Invoke-Req "La septima ya no cabe" 409 POST "/api/enrollments" $StudentToken @{
        academicPeriodId = $periodId; sectionIds = @($sectionIds[7])
    } | Out-Null

    $mias = Invoke-Req "El estudiante consulta sus inscripciones" 200 GET "/api/enrollments/me" $StudentToken $null
    Assert-True (@($mias | Where-Object { $_.status -eq "INSCRITA" }).Count -eq 6) `
        "Tiene 6 asignaturas inscritas" "tiene $(@($mias | Where-Object { $_.status -eq 'INSCRITA' }).Count)"

    $quedan = Invoke-Req "La oferta disponible ya no incluye las inscritas" 200 GET `
        "/api/enrollments/available?academicPeriodId=$periodId" $StudentToken $null
    Assert-True (@($quedan).Count -eq 2) "Quedan 2 disponibles" "quedan $(@($quedan).Count)"

    Invoke-Req "ADMIN consulta las inscripciones del estudiante" 200 GET `
        "/api/enrollments/student/$studentId" $AdminToken $null | Out-Null

    Write-Host ""
    Write-Host "=== ANULACION =================================================" -ForegroundColor Cyan

    Invoke-Req "Anular una inscripcion" 204 DELETE "/api/enrollments/$primeraId" $StudentToken $null | Out-Null
    Invoke-Req "Anular la misma otra vez" 409 DELETE "/api/enrollments/$primeraId" $StudentToken $null | Out-Null
    Invoke-Req "Anular una inscripcion inexistente" 404 DELETE "/api/enrollments/999999" $StudentToken $null | Out-Null

    $trasAnular = Invoke-Req "Releer las inscripciones" 200 GET "/api/enrollments/me" $StudentToken $null
    Assert-True (@($trasAnular | Where-Object { $_.status -eq "ANULADA" }).Count -eq 1) `
        "La anulada quedo registrada como ANULADA" "anuladas: $(@($trasAnular | Where-Object { $_.status -eq 'ANULADA' }).Count)"

    Invoke-Req "Volver a inscribir lo anulado (el cupo quedo libre)" 201 POST "/api/enrollments" $StudentToken @{
        academicPeriodId = $periodId; sectionIds = @($sectionIds[1])
    } | Out-Null

    Write-Host ""
    Write-Host "=== PERIODO CERRADO Y SEGURIDAD ===============================" -ForegroundColor Cyan

    Invoke-Req "Cerrar el segundo periodo" 200 PATCH "/api/academic-periods/$periodClosedId/status?status=CERRADO" $AdminToken $null | Out-Null

    Invoke-Req "No se inscribe en un periodo CERRADO" 409 POST "/api/enrollments" $StudentToken @{
        academicPeriodId = $periodClosedId; sectionIds = @($sectionIds[7])
    } | Out-Null

    Invoke-Req "La oferta de un periodo CERRADO no se consulta" 409 GET `
        "/api/enrollments/available?academicPeriodId=$periodClosedId" $StudentToken $null | Out-Null

    Invoke-Req "No se toca la matricula de un periodo CERRADO" 409 PUT "/api/student-period-status" $AdminToken @{
        studentId = $studentId; academicPeriodId = $periodClosedId; enrollmentStatus = "MATRICULATED"
    } | Out-Null

    Invoke-Req "ADMIN no inscribe en nombre del estudiante" 403 POST "/api/enrollments" $AdminToken @{
        academicPeriodId = $periodId; sectionIds = @($sectionIds[7])
    } | Out-Null

    Invoke-Req "STUDENT no consulta las inscripciones de otros" 403 GET `
        "/api/enrollments/student/$studentId" $StudentToken $null | Out-Null

    Invoke-Req "STUDENT no registra matriculas" 403 PUT "/api/student-period-status" $StudentToken @{
        studentId = $studentId; academicPeriodId = $periodId; enrollmentStatus = "MATRICULATED"
    } | Out-Null

    Invoke-Req "Sin token" 401 GET "/api/enrollments/me" "" $null | Out-Null

    Write-Host ""
    Write-Host "=== INTEGRIDAD REFERENCIAL ====================================" -ForegroundColor Cyan

    Invoke-Req "No se borra un estudiante con inscripciones" 409 DELETE "/api/students/$studentId" $AdminToken $null | Out-Null
    Invoke-Req "No se cambia de carrera con inscripciones" 409 PUT "/api/students/$studentId" $AdminToken @{
        run = $run1; fullName = "Estudiante Del Token"; email = $studentEmail; careerId = 999999
    } | Out-Null
    Invoke-Req "No se borra una seccion con inscritos" 409 DELETE "/api/sections/$($sectionIds[2])" $AdminToken $null | Out-Null
    Invoke-Req "No se borra una asignatura con inscripciones" 409 DELETE "/api/subjects/$($subjectIds[2])" $AdminToken $null | Out-Null
    Invoke-Req "No se borra un periodo con secciones" 409 DELETE "/api/academic-periods/$periodId" $AdminToken $null | Out-Null
    Invoke-Req "No se borra un periodo con matriculas" 409 DELETE "/api/academic-periods/$periodClosedId" $AdminToken $null | Out-Null
} else {
    Write-Host ""
    Write-Host "  [SKIP]        Todas las pruebas de inscripcion (faltan token de STUDENT o su email)" -ForegroundColor Yellow
}

# ---------------------------------------------------------------
# LIMPIEZA PARCIAL
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== LIMPIEZA PARCIAL ==========================================" -ForegroundColor Cyan

# Lo que no quedo inscrito si se puede borrar: asignaturas 7, 8 y 9.
foreach ($n in @(9, 8, 7)) {
    if ($sectionIds[$n]) {
        Invoke-Req "Borrar la seccion $n" 204 DELETE "/api/sections/$($sectionIds[$n])" $AdminToken $null | Out-Null
    }
}
if ($subjectIds[8] -and $subjectIds[1]) {
    Invoke-Req "Quitar el prerrequisito de la 8" 204 DELETE `
        "/api/subjects/$($subjectIds[8])/prerequisites/$($subjectIds[1])" $AdminToken $null | Out-Null
}
foreach ($n in @(9, 8, 7)) {
    if ($subjectIds[$n]) {
        Invoke-Req "Borrar la asignatura $n" 204 DELETE "/api/subjects/$($subjectIds[$n])" $AdminToken $null | Out-Null
    }
}

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
Write-Host "Residuo esperado en la base (por reglas de negocio, no se puede borrar):" -ForegroundColor DarkGray
Write-Host "  carrera $careerCode, plan P1-$stamp, periodos $year-1 y $year-2," -ForegroundColor DarkGray
Write-Host "  las asignaturas 1 a 6 con su seccion, el docente y el estudiante" -ForegroundColor DarkGray
Write-Host "  con sus inscripciones. Una inscripcion deja huella: ni el estudiante" -ForegroundColor DarkGray
Write-Host "  ni la seccion ni la asignatura se pueden eliminar despues." -ForegroundColor DarkGray
Write-Host ""
Write-Host "Pendiente hasta la Epica 7 (Grade / AcademicHistory):" -ForegroundColor DarkGray
Write-Host "  'no reinscribir una asignatura aprobada', 'prerrequisito aprobado'" -ForegroundColor DarkGray
Write-Host "  y 'no inscribir semestre superior con pendientes anteriores' solo se" -ForegroundColor DarkGray
Write-Host "  pueden probar de punta a punta cuando exista historial academico." -ForegroundColor DarkGray
Write-Host "  Tambien 'cupos agotados', que necesita un segundo estudiante." -ForegroundColor DarkGray
Write-Host "  Las tres estan cubiertas por EnrollmentServiceImplTest." -ForegroundColor DarkGray
Write-Host ""

if ($script:fail -gt 0) { exit 1 }
exit 0
