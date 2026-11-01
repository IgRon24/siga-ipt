# SIGA IPT - Prueba manual de Epica 7: Calificaciones y cierre de semestre
#
# Cubre /api/grades, /api/academic-closure y /api/academic-history: solo el
# docente responsable califica, una nota por inscripcion, escala 1.0 a 7.0,
# modificable mientras el periodo esta ABIERTO, el cierre exige todas las notas,
# consolida el historial y deja el periodo CERRADO sin retorno.
#
# Al final verifica de punta a punta las tres reglas de la Epica 6 que solo se
# pueden probar con historial: no reinscribir una asignatura aprobada, el
# prerrequisito aprobado y no inscribir semestre superior con pendientes abajo.
#
# NECESITA TRES TOKENS: ADMIN (preparacion y cierre), TEACHER (las notas, se
# identifica por el email del token) y STUDENT (las inscripciones).
#
# Uso desde cmd (login automatico contra Keycloak):
#   set KC_CLIENT_ID=tu-client-id
#   set KC_ADMIN_USER=tu-usuario-admin
#   set KC_ADMIN_PASS=tu-password
#   set KC_TEACHER_USER=tu-usuario-teacher
#   set KC_TEACHER_PASS=tu-password
#   set KC_STUDENT_USER=tu-usuario-student
#   set KC_STUDENT_PASS=tu-password
#   powershell -ExecutionPolicy Bypass -File http\grades.ps1

param(
    [string]$BaseUrl      = "http://localhost:8080",
    [string]$AdminToken   = $env:ADMIN_TOKEN,
    [string]$TeacherToken = $env:TEACHER_TOKEN,
    [string]$StudentToken = $env:STUDENT_TOKEN,

    [string]$KeycloakUrl  = "http://localhost:8081",
    [string]$Realm        = "siga-realm",
    [string]$ClientId     = $env:KC_CLIENT_ID,
    [string]$ClientSecret = $env:KC_CLIENT_SECRET,
    [string]$AdminUser    = $env:KC_ADMIN_USER,
    [string]$AdminPass    = $env:KC_ADMIN_PASS,
    [string]$TeacherUser  = $env:KC_TEACHER_USER,
    [string]$TeacherPass  = $env:KC_TEACHER_PASS,
    [string]$StudentUser  = $env:KC_STUDENT_USER,
    [string]$StudentPass  = $env:KC_STUDENT_PASS
)

$ErrorActionPreference = "Stop"
$script:pass = 0
$script:fail = 0

$stamp      = Get-Date -Format "HHmmss"
$short      = $stamp.Substring(2, 4)
$careerCode = "4" + $short
$run1       = ("2" + $stamp + "2").Substring(0, 8) + "-9"
$teacherRun = ("8" + $stamp + "8").Substring(0, 8) + "-5"
# El cierre no tiene retorno, asi que cada ejecucion necesita su propio par de
# periodos ABIERTO. El anio se elige mas abajo, buscando uno todavia libre.

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
if (-not $TeacherToken -and $TeacherUser) {
    $TeacherToken = Get-KeycloakToken -User $TeacherUser -Pass $TeacherPass -Label "TEACHER"
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

$teacherEmail = $null
if ($TeacherToken) { $teacherEmail = Get-JwtEmail -Token $TeacherToken }
$studentEmail = $null
if ($StudentToken) { $studentEmail = Get-JwtEmail -Token $StudentToken }

if (-not $teacherEmail -or -not $studentEmail) {
    Write-Host ""
    Write-Host "ERROR: la Epica 7 necesita token de TEACHER y de STUDENT con claim email." -ForegroundColor Red
    Write-Host "  Las notas las registra el docente responsable y las inscripciones el estudiante."
    Write-Host "  set KC_TEACHER_USER / KC_TEACHER_PASS / KC_STUDENT_USER / KC_STUDENT_PASS"
    Write-Host ""
    exit 1
}

# ---------------------------------------------------------------
# PREPARACION
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== PREPARACION ===============================================" -ForegroundColor Cyan

# Una inscripcion deja al estudiante atado a su plan y no se puede mover de carrera,
# asi que si el usuario del token ya existe se trabaja sobre SU carrera y SU plan.
# Esto es lo que hace repetible al script.
$allStudents = Invoke-Quiet GET "/api/students" $AdminToken $null
$student     = @($allStudents) | Where-Object { $_.email -eq $studentEmail } | Select-Object -First 1
$studentId   = $null

if ($student) {
    $studentId = $student.id
    $careerId  = $student.careerId
    $planId    = $student.studyPlanId
    Write-Host "  [OK]         Estudiante reutilizado: id $studentId, carrera $($student.careerCode), plan $($student.studyPlanIdentifier)" -ForegroundColor Green
    Write-Host "               se crean asignaturas nuevas en ese plan" -ForegroundColor DarkGray
    $script:pass++
} else {
    $career = Invoke-Req "Crear carrera $careerCode" 201 POST "/api/careers" $AdminToken @{
        code = $careerCode; name = "Carrera grades.ps1 $stamp"
        description = "Carrera creada por grades.ps1"; status = "ACTIVA"
    }
    if (-not $career) { Write-Host "No se pudo crear la carrera, se aborta." -ForegroundColor Red; exit 1 }
    $careerId = $career.id

    $plan = Invoke-Req "Crear plan VIGENTE" 201 POST "/api/study-plans" $AdminToken @{
        identifier = "P7-$stamp"; name = "Plan calificaciones"; status = "VIGENTE"; careerId = $careerId
    }
    if (-not $plan) { Write-Host "No se pudo crear el plan, se aborta." -ForegroundColor Red; exit 1 }
    $planId = $plan.id
}

# Dos periodos libres y consecutivos: el primero se cierra y el segundo prueba la
# inscripcion con historial.
$usedCodes = @()
$existingPeriods = Invoke-Quiet GET "/api/academic-periods" $AdminToken $null
if ($existingPeriods) { $usedCodes = @($existingPeriods) | ForEach-Object { $_.code } }

$year = $null
foreach ($candidate in 2000..2100) {
    if (($usedCodes -notcontains "$candidate-1") -and ($usedCodes -notcontains "$candidate-2")) {
        $year = $candidate
        break
    }
}
if (-not $year) {
    Write-Host "No quedan anios libres entre 2000 y 2100 para crear los periodos." -ForegroundColor Red
    exit 1
}

$period = Invoke-Req "Crear periodo $year-1" 201 POST "/api/academic-periods" $AdminToken @{
    year = $year; semester = 1
}
if (-not $period) { Write-Host "No se pudo crear el periodo $year-1, se aborta." -ForegroundColor Red; exit 1 }
$periodId = $period.id

# El docente del token es el responsable de las secciones; el segundo docente
# sirve para probar que nadie califica una seccion ajena.
$teacher = Invoke-Quiet POST "/api/teachers" $AdminToken @{
    run = $teacherRun; fullName = "Docente Del Token"; email = $teacherEmail
    professionalTitle = "Ingeniero"; academicDegree = "Magister"
}
if ($teacher) {
    Write-Host "  [OK]         Docente creado para el email del token" -ForegroundColor Green
    $script:pass++
    $teacherId = $teacher.id
} else {
    $allTeachers = Invoke-Quiet GET "/api/teachers" $AdminToken $null
    $existing = @($allTeachers) | Where-Object { $_.email -eq $teacherEmail } | Select-Object -First 1
    if ($existing) {
        Write-Host "  [OK]         Docente reutilizado de una corrida anterior (id $($existing.id))" -ForegroundColor Green
        $script:pass++
        $teacherId = $existing.id
    } else {
        Write-Host "  [FALLA]      No se pudo crear ni encontrar al docente del token" -ForegroundColor Red
        $script:fail++
        exit 1
    }
}

# El backend resuelve al docente por el claim "email", igual que /students/me.
$me = Invoke-Req "El docente se reconoce en /api/teachers/me" 200 GET "/api/teachers/me" $TeacherToken $null
Assert-True ($me -and $me.id -eq $teacherId) "Es el docente del email del token" `
    "llego id '$($me.id)'"
Invoke-Req "El estudiante no tiene ficha de docente" 404 GET "/api/teachers/me" $StudentToken $null | Out-Null

$other = Invoke-Req "Crear un segundo docente" 201 POST "/api/teachers" $AdminToken @{
    run = ("9" + $stamp + "9").Substring(0, 8) + "-3"; fullName = "Otro Docente"
    email = "otro.docente.$stamp@ipt.cl"
    professionalTitle = "Ingeniero"; academicDegree = "Licenciado"
}
$otherTeacherId = $other.id

# 4 asignaturas de semestre 1 (TEL 2+2+0 = 4 horas -> 2 bloques) sin choques y
# 1 de semestre 2 que exige la primera como prerrequisito.
$layout = @(
    @{ n = 1; day1 = "L"; mod1 = 1; day2 = "L"; mod2 = 2; sem = 1 },
    @{ n = 2; day1 = "M"; mod1 = 1; day2 = "M"; mod2 = 2; sem = 1 },
    @{ n = 3; day1 = "W"; mod1 = 1; day2 = "W"; mod2 = 2; sem = 1 },
    @{ n = 4; day1 = "J"; mod1 = 1; day2 = "J"; mod2 = 2; sem = 1 },
    @{ n = 5; day1 = "V"; mod1 = 1; day2 = "V"; mod2 = 2; sem = 2 }
)

$subjectIds  = @{}
$subjectCode = @{}
$sectionIds  = @{}
foreach ($row in $layout) {
    $n = $row.n
    $code = "GRA$n-$stamp"
    $subject = Invoke-Quiet POST "/api/subjects" $AdminToken @{
        code = $code; name = "Asignatura $n"; semester = $row.sem
        telTheory = 2; telExercises = 2; telLab = 0; sct = 4; studyPlanId = $planId
    }
    if (-not $subject) { Write-Host "No se pudo crear la asignatura $n, se aborta." -ForegroundColor Red; exit 1 }
    $subjectIds[$n]  = $subject.id
    $subjectCode[$n] = $code

    # La 5 (semestre 2) no se ofrece en este periodo: queda para el periodo siguiente.
    if ($n -le 4) {
        # La 4 parte con el otro docente, para probar la seccion ajena.
        $assigned = $teacherId
        if ($n -eq 4) { $assigned = $otherTeacherId }

        $section = Invoke-Quiet POST "/api/sections" $AdminToken @{
            subjectId = $subject.id; academicPeriodId = $periodId; teacherId = $assigned
            capacity = 30
            scheduleBlocks = @((New-Block $row.day1 $row.mod1), (New-Block $row.day2 $row.mod2))
        }
        if (-not $section) { Write-Host "No se pudo crear la seccion $n, se aborta." -ForegroundColor Red; exit 1 }
        $sectionIds[$n] = $section.id
    }
}
Write-Host "  [OK]         5 asignaturas creadas, 4 con seccion en $year-1" -ForegroundColor Green
$script:pass++

Invoke-Req "La asignatura 5 (sem 2) exige la 1 (sem 1)" 201 POST `
    "/api/subjects/$($subjectIds[5])/prerequisites" $AdminToken @{ prerequisiteSubjectId = $subjectIds[1] } | Out-Null

# ---------------------------------------------------------------
# ESTUDIANTE, MATRICULA E INSCRIPCION
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== ESTUDIANTE E INSCRIPCION ==================================" -ForegroundColor Cyan

if (-not $studentId) {
    $created = Invoke-Req "Crear al estudiante del email del token" 201 POST "/api/students" $AdminToken @{
        run = $run1; fullName = "Estudiante Del Token"; email = $studentEmail; careerId = $careerId
    }
    if (-not $created) { Write-Host "No se pudo crear al estudiante, se aborta." -ForegroundColor Red; exit 1 }
    $studentId = $created.id
}

$studentNow = Invoke-Req "El estudiante esta REGULAR y puede inscribir" 200 GET `
    "/api/students/$studentId" $AdminToken $null

# El RUN sale del estudiante real: si se reutilizo, no es el que iba a generar el script.
$studentRun = $studentNow.run
if (-not $studentNow -or $studentNow.status -ne "REGULAR") {
    Write-Host "El estudiante quedo $($studentNow.status) en una corrida anterior:" -ForegroundColor Red
    Write-Host "  EGRESADO y ELIMINADO son terminales y ya no puede inscribir." -ForegroundColor DarkGray
    Write-Host "  Usa otro usuario STUDENT de Keycloak para volver a correr el script." -ForegroundColor DarkGray
    exit 1
}

Invoke-Req "Matricular al estudiante en $year-1" 200 PUT "/api/student-period-status" $AdminToken @{
    studentId = $studentId; academicPeriodId = $periodId; enrollmentStatus = "MATRICULATED"
} | Out-Null

$enrolled = Invoke-Req "Inscribir las 4 asignaturas del semestre 1" 201 POST "/api/enrollments" $StudentToken @{
    academicPeriodId = $periodId
    sectionIds = @($sectionIds[1], $sectionIds[2], $sectionIds[3], $sectionIds[4])
}
if (-not $enrolled) { Write-Host "No se pudo inscribir, se aborta." -ForegroundColor Red; exit 1 }

# Inscripcion por asignatura, para calificar cada una.
$enrollmentIds = @{}
foreach ($row in @($enrolled)) {
    foreach ($n in 1..4) {
        if ($row.sectionId -eq $sectionIds[$n]) { $enrollmentIds[$n] = $row.id }
    }
}
Assert-True ($enrollmentIds.Count -eq 4) "Las 4 inscripciones quedaron identificadas" `
    "se identificaron $($enrollmentIds.Count)"

# ---------------------------------------------------------------
# ACTA DE LA SECCION
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== ACTA DE LA SECCION ========================================" -ForegroundColor Cyan

$sheet = Invoke-Req "El docente ve el acta de su seccion 1" 200 GET `
    "/api/grades/section/$($sectionIds[1])" $TeacherToken $null
Assert-True ($sheet -and @($sheet).Count -eq 1) "El acta trae la unica inscripcion de la seccion" `
    "llegaron $(@($sheet).Count) filas"
Assert-True ($sheet -and $null -eq @($sheet)[0].value) "La inscripcion aparece todavia sin nota" `
    "llego value '$(@($sheet)[0].value)'"
Assert-True ($sheet -and @($sheet)[0].studentRun -eq $studentRun) "El acta identifica al estudiante" `
    "llego '$(@($sheet)[0].studentRun)', se esperaba '$studentRun'"

Invoke-Req "El docente no ve el acta de una seccion ajena" 409 GET `
    "/api/grades/section/$($sectionIds[4])" $TeacherToken $null | Out-Null

Invoke-Req "El ADMIN si consulta cualquier acta" 200 GET `
    "/api/grades/section/$($sectionIds[4])" $AdminToken $null | Out-Null

Invoke-Req "El estudiante no accede al acta" 403 GET `
    "/api/grades/section/$($sectionIds[1])" $StudentToken $null | Out-Null

Invoke-Req "404 si la seccion no existe" 404 GET "/api/grades/section/999999" $TeacherToken $null | Out-Null

# ---------------------------------------------------------------
# REGISTRO DE CALIFICACIONES
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== REGISTRO DE CALIFICACIONES ================================" -ForegroundColor Cyan

Invoke-Req "Rechaza una nota sobre 7.0" 400 POST "/api/grades" $TeacherToken @{
    enrollmentId = $enrollmentIds[1]; value = 7.5
} | Out-Null

Invoke-Req "Rechaza una nota bajo 1.0" 400 POST "/api/grades" $TeacherToken @{
    enrollmentId = $enrollmentIds[1]; value = 0.5
} | Out-Null

Invoke-Req "Rechaza una nota sin valor" 400 POST "/api/grades" $TeacherToken @{
    enrollmentId = $enrollmentIds[1]
} | Out-Null

Invoke-Req "El docente no califica una seccion ajena" 409 POST "/api/grades" $TeacherToken @{
    enrollmentId = $enrollmentIds[4]; value = 5.0
} | Out-Null

Invoke-Req "El ADMIN no registra notas" 403 POST "/api/grades" $AdminToken @{
    enrollmentId = $enrollmentIds[1]; value = 5.0
} | Out-Null

Invoke-Req "El estudiante no registra notas" 403 POST "/api/grades" $StudentToken @{
    enrollmentId = $enrollmentIds[1]; value = 7.0
} | Out-Null

Invoke-Req "404 si la inscripcion no existe" 404 POST "/api/grades" $TeacherToken @{
    enrollmentId = 999999; value = 5.0
} | Out-Null

$grade1 = Invoke-Req "Registrar un 4.5 en la asignatura 1" 201 POST "/api/grades" $TeacherToken @{
    enrollmentId = $enrollmentIds[1]; value = 4.5
}
Assert-True ($grade1 -and $grade1.result -eq "APROBADO") "4.5 queda APROBADO" `
    "llego '$($grade1.result)'"
Assert-True ($grade1 -and [decimal]$grade1.value -eq 4.5) "La nota quedo en 4.5" `
    "llego '$($grade1.value)'"
$gradeId1 = $grade1.id

Invoke-Req "Una inscripcion no admite una segunda nota" 409 POST "/api/grades" $TeacherToken @{
    enrollmentId = $enrollmentIds[1]; value = 6.0
} | Out-Null

$lowered = Invoke-Req "Corregir la nota a 3.9 mientras el periodo esta ABIERTO" 200 PUT `
    "/api/grades/$gradeId1" $TeacherToken @{ enrollmentId = $enrollmentIds[1]; value = 3.9 }
Assert-True ($lowered -and $lowered.result -eq "REPROBADO") "3.9 queda REPROBADO" `
    "llego '$($lowered.result)'"
Assert-True ($lowered -and $lowered.updatedAt) "La correccion deja fecha de modificacion" `
    "llego updatedAt '$($lowered.updatedAt)'"

$restored = Invoke-Req "Volver a dejarla en 4.5" 200 PUT `
    "/api/grades/$gradeId1" $TeacherToken @{ enrollmentId = $enrollmentIds[1]; value = 4.5 }
Assert-True ($restored -and $restored.result -eq "APROBADO") "4.5 vuelve a quedar APROBADO" `
    "llego '$($restored.result)'"

Invoke-Req "La nota no se traspasa a otra inscripcion" 409 PUT "/api/grades/$gradeId1" $TeacherToken @{
    enrollmentId = $enrollmentIds[2]; value = 5.0
} | Out-Null

Invoke-Req "404 si la calificacion no existe" 404 PUT "/api/grades/999999" $TeacherToken @{
    enrollmentId = $enrollmentIds[1]; value = 5.0
} | Out-Null

$sheet = Invoke-Req "El acta ya muestra la nota registrada" 200 GET `
    "/api/grades/section/$($sectionIds[1])" $TeacherToken $null
Assert-True ($sheet -and [decimal]@($sheet)[0].value -eq 4.5) "El acta trae el 4.5" `
    "llego '$(@($sheet)[0].value)'"

Invoke-Req "Una inscripcion ya calificada no se anula" 409 DELETE `
    "/api/enrollments/$($enrollmentIds[1])" $StudentToken $null | Out-Null

# ---------------------------------------------------------------
# CIERRE DE PERIODO
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== CIERRE DE PERIODO =========================================" -ForegroundColor Cyan

$pending = Invoke-Req "No se cierra con notas pendientes" 409 POST `
    "/api/academic-closure/$periodId" $AdminToken $null
Assert-True ($pending -and $pending.message -like "*pending grades*") `
    "El error informa las asignaturas sin nota" "llego '$($pending.message)'"

# El diagnostico dice lo mismo que el 409, pero sin intentar el cierre
$notReady = Invoke-Req "El diagnostico avisa que todavia no se puede cerrar" 200 GET `
    "/api/academic-periods/$periodId/closure-readiness" $AdminToken $null
Assert-True ($notReady -and -not $notReady.canClose) "canClose es falso" `
    "llego '$($notReady.canClose)'"
Assert-True ($notReady -and $notReady.pendingCount -gt 0) "Informa cuantas notas faltan" `
    "llego '$($notReady.pendingCount)'"
Assert-True ($notReady -and @($notReady.pendingSubjectCodes).Count -gt 0) `
    "Nombra las asignaturas que bloquean el cierre" "no llego ninguna"
Assert-True ($notReady -and ($pending.message -like "*$(@($notReady.pendingSubjectCodes)[0])*")) `
    "Son las mismas asignaturas que lista el 409" `
    "el 409 no menciona '$(@($notReady.pendingSubjectCodes)[0])'"
Assert-True ($notReady -and $notReady.sectionCount -eq 4) "Trae las 4 secciones del periodo" `
    "llegaron '$($notReady.sectionCount)'"

Invoke-Req "El docente no pide el diagnostico del cierre" 403 GET `
    "/api/academic-periods/$periodId/closure-readiness" $TeacherToken $null | Out-Null
Invoke-Req "El estudiante no pide el diagnostico del cierre" 403 GET `
    "/api/academic-periods/$periodId/closure-readiness" $StudentToken $null | Out-Null
Invoke-Req "404 si el periodo del diagnostico no existe" 404 GET `
    "/api/academic-periods/999999/closure-readiness" $AdminToken $null | Out-Null

Invoke-Req "PATCH a CERRADO tampoco esquiva el cierre" 409 PATCH `
    "/api/academic-periods/$periodId/status?status=CERRADO" $AdminToken $null | Out-Null

Invoke-Req "Registrar un 6.0 en la asignatura 2" 201 POST "/api/grades" $TeacherToken @{
    enrollmentId = $enrollmentIds[2]; value = 6.0
} | Out-Null

Invoke-Req "Registrar un 2.5 en la asignatura 3" 201 POST "/api/grades" $TeacherToken @{
    enrollmentId = $enrollmentIds[3]; value = 2.5
} | Out-Null

# La 4 era del otro docente: el ADMIN la reasigna para poder calificarla.
Invoke-Req "Reasignar la seccion 4 al docente del token" 200 PUT `
    "/api/sections/$($sectionIds[4])" $AdminToken @{
        subjectId = $subjectIds[4]; academicPeriodId = $periodId; teacherId = $teacherId
        capacity = 30
        scheduleBlocks = @((New-Block "J" 1), (New-Block "J" 2))
    } | Out-Null

Invoke-Req "Registrar un 5.0 en la asignatura 4" 201 POST "/api/grades" $TeacherToken @{
    enrollmentId = $enrollmentIds[4]; value = 5.0
} | Out-Null

$ready = Invoke-Req "Con las 4 notas el diagnostico habilita el cierre" 200 GET `
    "/api/academic-periods/$periodId/closure-readiness" $AdminToken $null
Assert-True ($ready -and $ready.canClose) "canClose es verdadero" "llego '$($ready.canClose)'"
Assert-True ($ready -and $ready.pendingCount -eq 0) "No queda ninguna nota pendiente" `
    "llegaron '$($ready.pendingCount)'"
Assert-True ($ready -and $ready.enrolledCount -eq 4) "Cuenta las 4 inscripciones INSCRITA" `
    "llegaron '$($ready.enrolledCount)'"
Assert-True ($ready -and $ready.gradedCount -eq 4) "Las 4 con nota" "llegaron '$($ready.gradedCount)'"

Invoke-Req "El docente no cierra el periodo" 403 POST "/api/academic-closure/$periodId" $TeacherToken $null | Out-Null
Invoke-Req "El estudiante no cierra el periodo" 403 POST "/api/academic-closure/$periodId" $StudentToken $null | Out-Null
Invoke-Req "404 si el periodo a cerrar no existe" 404 POST "/api/academic-closure/999999" $AdminToken $null | Out-Null

$closure = Invoke-Req "Cerrar el periodo con las 4 notas registradas" 200 POST `
    "/api/academic-closure/$periodId" $AdminToken $null
Assert-True ($closure -and $closure.status -eq "CERRADO") "El periodo quedo CERRADO" `
    "llego '$($closure.status)'"
Assert-True ($closure -and $closure.consolidatedEnrollments -eq 4) "Se consolidaron las 4 inscripciones" `
    "llego '$($closure.consolidatedEnrollments)'"
Assert-True ($closure -and $closure.approvedCount -eq 3) "3 aprobadas" "llego '$($closure.approvedCount)'"
Assert-True ($closure -and $closure.failedCount -eq 1) "1 reprobada" "llego '$($closure.failedCount)'"
Assert-True ($closure -and @($closure.graduatedStudentRuns).Count -eq 0) `
    "Nadie egresa: queda la asignatura 5 del plan" "llegaron $(@($closure.graduatedStudentRuns).Count)"
Assert-True ($closure -and @($closure.eliminatedStudentRuns).Count -eq 0) `
    "Nadie eliminado: es la primera reprobacion" "llegaron $(@($closure.eliminatedStudentRuns).Count)"

$studentAfter = Invoke-Req "El estudiante sigue REGULAR" 200 GET "/api/students/$studentId" $AdminToken $null
Assert-True ($studentAfter -and $studentAfter.status -eq "REGULAR") "Su estado no cambio" `
    "llego '$($studentAfter.status)'"

Invoke-Req "Un periodo CERRADO no se cierra de nuevo" 409 POST `
    "/api/academic-closure/$periodId" $AdminToken $null | Out-Null

$afterClosure = Invoke-Req "El diagnostico de un periodo CERRADO no ofrece cierre" 200 GET `
    "/api/academic-periods/$periodId/closure-readiness" $AdminToken $null
Assert-True ($afterClosure -and $afterClosure.status -eq "CERRADO") "El diagnostico lo ve CERRADO" `
    "llego '$($afterClosure.status)'"
Assert-True ($afterClosure -and -not $afterClosure.canClose) "canClose es falso aunque no falten notas" `
    "llego '$($afterClosure.canClose)'"

Invoke-Req "Con el periodo CERRADO la nota ya no se modifica" 409 PUT `
    "/api/grades/$gradeId1" $TeacherToken @{ enrollmentId = $enrollmentIds[1]; value = 7.0 } | Out-Null

Invoke-Req "Con el periodo CERRADO no se anula una inscripcion" 409 DELETE `
    "/api/enrollments/$($enrollmentIds[2])" $StudentToken $null | Out-Null

Invoke-Req "Un periodo CERRADO no se elimina" 409 DELETE "/api/academic-periods/$periodId" $AdminToken $null | Out-Null

# ---------------------------------------------------------------
# HISTORIAL ACADEMICO
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== HISTORIAL ACADEMICO =======================================" -ForegroundColor Cyan

$history = Invoke-Req "El estudiante consulta su historial" 200 GET "/api/academic-history/me" $StudentToken $null

# Solo las filas de este periodo: un estudiante reutilizado arrastra corridas anteriores.
$history = @($history) | Where-Object { $_.academicPeriodCode -eq "$year-1" }
Assert-True (@($history).Count -eq 4) "El historial trae las 4 asignaturas cursadas en $year-1" `
    "llegaron $(@($history).Count) filas"

$row1 = @($history) | Where-Object { $_.subjectCode -eq $subjectCode[1] } | Select-Object -First 1
Assert-True ($row1 -and [decimal]$row1.grade -eq 4.5 -and $row1.result -eq "APROBADO") `
    "La asignatura 1 quedo 4.5 APROBADO" "llego '$($row1.grade)' / '$($row1.result)'"

$row3 = @($history) | Where-Object { $_.subjectCode -eq $subjectCode[3] } | Select-Object -First 1
Assert-True ($row3 -and [decimal]$row3.grade -eq 2.5 -and $row3.result -eq "REPROBADO") `
    "La asignatura 3 quedo 2.5 REPROBADO" "llego '$($row3.grade)' / '$($row3.result)'"
Assert-True ($row3 -and $row3.academicPeriodCode -eq "$year-1") "El historial guarda el periodo" `
    "llego '$($row3.academicPeriodCode)'"
Assert-True ($row3 -and $row3.subjectName -eq "Asignatura 3") "El snapshot guarda el nombre de la asignatura" `
    "llego '$($row3.subjectName)'"

Invoke-Req "El ADMIN consulta el historial de un estudiante" 200 GET `
    "/api/academic-history/student/$studentId" $AdminToken $null | Out-Null
Invoke-Req "El docente tambien lo consulta" 200 GET `
    "/api/academic-history/student/$studentId" $TeacherToken $null | Out-Null
Invoke-Req "404 si el estudiante no existe" 404 GET "/api/academic-history/student/999999" $AdminToken $null | Out-Null

# Una asignatura que ya esta en el historial no se puede eliminar.
Invoke-Req "No se elimina una asignatura con historial" 409 DELETE `
    "/api/subjects/$($subjectIds[1])" $AdminToken $null | Out-Null
Invoke-Req "No se elimina un estudiante con historial" 409 DELETE `
    "/api/students/$studentId" $AdminToken $null | Out-Null

# ---------------------------------------------------------------
# LO QUE EL HISTORIAL DESBLOQUEA EN LA EPICA 6
# ---------------------------------------------------------------

Write-Host ""
Write-Host "=== INSCRIPCION CON HISTORIAL (Epica 6) =======================" -ForegroundColor Cyan

$period2 = Invoke-Req "Crear el periodo $year-2" 201 POST "/api/academic-periods" $AdminToken @{
    year = $year; semester = 2
}
$period2Id = $period2.id

Invoke-Req "Matricular al estudiante en $year-2" 200 PUT "/api/student-period-status" $AdminToken @{
    studentId = $studentId; academicPeriodId = $period2Id; enrollmentStatus = "MATRICULATED"
} | Out-Null

# Oferta del periodo 2: la 1 (aprobada), la 3 (reprobada) y la 5 (sem 2, prerrequisito aprobado).
$offer2 = @(
    @{ n = 1; day = "L"; m1 = 1; m2 = 2 },
    @{ n = 3; day = "M"; m1 = 1; m2 = 2 },
    @{ n = 5; day = "W"; m1 = 1; m2 = 2 }
)
$section2Ids = @{}
foreach ($row in $offer2) {
    $section = Invoke-Quiet POST "/api/sections" $AdminToken @{
        subjectId = $subjectIds[$row.n]; academicPeriodId = $period2Id; teacherId = $teacherId
        capacity = 30
        scheduleBlocks = @((New-Block $row.day $row.m1), (New-Block $row.day $row.m2))
    }
    if (-not $section) { Write-Host "No se pudo crear la seccion $($row.n) del periodo 2." -ForegroundColor Red; exit 1 }
    $section2Ids[$row.n] = $section.id
}
Write-Host "  [OK]         3 secciones creadas en $year-2 (asignaturas 1, 3 y 5)" -ForegroundColor Green
$script:pass++

$available = Invoke-Req "Oferta disponible para el estudiante en $year-2" 200 GET `
    "/api/enrollments/available?academicPeriodId=$period2Id" $StudentToken $null
$codes = @($available) | ForEach-Object { $_.subjectCode }
Assert-True (@($codes).Count -eq 2) "La oferta trae 2 asignaturas, no 3" "llegaron $(@($codes).Count): $($codes -join ', ')"
Assert-True ($codes -notcontains $subjectCode[1]) "La asignatura 1 no aparece: esta APROBADA" `
    "la oferta trajo $($codes -join ', ')"
Assert-True ($codes -contains $subjectCode[3]) "La asignatura 3 aparece: esta REPROBADA" `
    "la oferta trajo $($codes -join ', ')"
Assert-True ($codes -contains $subjectCode[5]) "La asignatura 5 aparece: su prerrequisito esta APROBADO" `
    "la oferta trajo $($codes -join ', ')"

Invoke-Req "No se reinscribe una asignatura ya aprobada" 409 POST "/api/enrollments" $StudentToken @{
    academicPeriodId = $period2Id; sectionIds = @($section2Ids[1])
} | Out-Null

Invoke-Req "No se inscribe semestre superior con pendientes abajo" 409 POST "/api/enrollments" $StudentToken @{
    academicPeriodId = $period2Id; sectionIds = @($section2Ids[5])
} | Out-Null

$enrolled2 = Invoke-Req "Inscribir las 2 disponibles (el minimo baja de 3 a 2)" 201 POST `
    "/api/enrollments" $StudentToken @{
        academicPeriodId = $period2Id; sectionIds = @($section2Ids[3], $section2Ids[5])
    }
Assert-True ($enrolled2 -and @($enrolled2).Count -eq 2) "Quedaron 2 inscripciones en $year-2" `
    "llegaron $(@($enrolled2).Count)"

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
Write-Host "  carrera $($studentNow.careerCode), plan $($studentNow.studyPlanIdentifier), periodos $year-1 (CERRADO) y $year-2," -ForegroundColor DarkGray
Write-Host "  las 5 asignaturas con sus secciones, los dos docentes y el estudiante" -ForegroundColor DarkGray
Write-Host "  con sus notas e historial academico. Un periodo CERRADO y el historial" -ForegroundColor DarkGray
Write-Host "  son informacion historica: nada de eso se elimina." -ForegroundColor DarkGray
Write-Host ""
Write-Host "No cubierto por este script:" -ForegroundColor DarkGray
Write-Host "  EGRESADO (aprobar todo el plan) y ELIMINADO (reprobar la misma" -ForegroundColor DarkGray
Write-Host "  asignatura dos veces) necesitan dos periodos cerrados seguidos;" -ForegroundColor DarkGray
Write-Host "  ambos estan cubiertos por AcademicClosureServiceImplTest." -ForegroundColor DarkGray
Write-Host ""

if ($script:fail -gt 0) { exit 1 }
exit 0
