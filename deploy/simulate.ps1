# SIGA IPT - Simulacion de un instituto en marcha.
#
# deploy/seed.sh deja el catalogo (3 carreras, 72 asignaturas, 2 periodos, unos
# pocos usuarios). Este script lo convierte en algo que se parece a un sistema
# en produccion, y lo hace TODO por la API, con el token del rol que
# corresponde: nada se escribe a mano en la base.
#
# La historia que arma, en tres periodos:
#
#   2025-1  Primer periodo del instituto. Cohorte A (15 estudiantes, 5 por
#           carrera) cursa el semestre 1 completo. El script lo CIERRA, asi que
#           el historial academico que queda lo genero el propio sistema: hay
#           aprobados y hay reprobados (2 estudiantes por carrera arrastran
#           asignaturas).
#   2025-2  Ya existe cerrado desde seed.sh (Pedro Munoz, semestre 1 de TAP).
#   2026-1  ABIERTO, la vitrina. Oferta de los semestres 1 y 2 de los tres
#           planes, repartida entre 12 docentes activos. Dentro conviven:
#             - la cohorte A en semestre 2, y los repitentes arrastrando su
#               asignatura reprobada (la regla de "semestres inferiores primero"
#               los obliga a tomarla antes que las de segundo);
#             - una cohorte B de 9 novatos en semestre 1;
#             - secciones con la nota puesta, secciones a medio calificar y
#               secciones sin calificar, para que el cierre tenga algo que
#               reclamar y el docente algo que hacer;
#             - un caso de ELIMINADO armado: una estudiante que reprobo en
#               2025-1 vuelve a reprobar LA MISMA asignatura, asi que el dia que
#               se cierre 2026-1 el sistema la elimina. Es la unica forma de
#               mostrar esa regla, porque exige dos periodos.
#
# Lo que NO se puede mostrar con estos datos es un EGRESADO: egresa quien
# aprueba las 24 asignaturas de su plan, y el instituto simulado lleva dos
# periodos, asi que nadie alcanza el semestre 3. La regla esta implementada y
# cubierta por los tests; con datos reales tomaria cuatro periodos.
#
# IDEMPOTENTE: se puede volver a correr. Lo que existe no se duplica (los
# usuarios se buscan por email, las secciones por asignatura+periodo, las notas
# por inscripcion sin calificar).
#
# IRREVERSIBLE: cerrar 2025-1 no tiene vuelta, y el historial academico no se
# borra -- es la regla del dominio, no una limitacion del script. Para volver a
# cero: docker compose down -v && docker compose up -d && deploy/seed.sh.
#
# Uso desde cmd, con el stack arriba y deploy/seed.sh ya corrido:
#
#   set KEYCLOAK_ADMIN=admin
#   set KEYCLOAK_ADMIN_PASSWORD=...      REM admin de la consola de Keycloak
#   set SIGA_ADMIN_PASS=...              REM usuario admin del realm (rol ADMIN)
#   set SIGA_TEACHER_PASS=...            REM usuario teacher (Ana Perez)
#   set SIGA_STUDENT_PASS=...            REM usuario student (Pedro Munoz)
#   set SIGA_SIM_PASS=...                REM password de todos los que cree
#   powershell -ExecutionPolicy Bypass -File deploy\simulate.ps1
#
# Los docentes y estudiantes que crea quedan tambien en Keycloak, con usuario =
# la parte local del email (claudio.herrera, matias.fuentes, ...) y la password
# de SIGA_SIM_PASS, asi que se puede entrar al sistema como cualquiera de ellos.

param(
    [string]$BaseUrl     = "http://localhost",
    [string]$KeycloakUrl,
    [string]$Realm       = "siga-realm",

    # Admin de la consola de Keycloak (realm master): crea los usuarios.
    [string]$KcAdminUser = $env:KEYCLOAK_ADMIN,
    [string]$KcAdminPass = $env:KEYCLOAK_ADMIN_PASSWORD,

    [string]$ClientId    = $env:KC_CLIENT_ID,

    # Los tres usuarios que ya trae el realm. Cada rol hace lo suyo: el ADMIN
    # arma el catalogo y cierra, el estudiante inscribe (su id sale del token) y
    # el docente responsable califica.
    [string]$AdminUser   = $env:SIGA_ADMIN_USER,
    [string]$AdminPass   = $env:SIGA_ADMIN_PASS,
    [string]$TeacherUser = $env:SIGA_TEACHER_USER,
    [string]$TeacherPass = $env:SIGA_TEACHER_PASS,
    [string]$StudentUser = $env:SIGA_STUDENT_USER,
    [string]$StudentPass = $env:SIGA_STUDENT_PASS,

    # Password de todos los usuarios que cree el script.
    [string]$SimPassword = $env:SIGA_SIM_PASS,

    # No crear ni cerrar 2025-1 (solo poblar el periodo abierto).
    [switch]$SkipPast
)

$ErrorActionPreference = "Stop"
if (-not $KeycloakUrl) { $KeycloakUrl = "$BaseUrl/auth" }
if (-not $ClientId)    { $ClientId    = "siga-frontend" }
if (-not $AdminUser)   { $AdminUser   = "admin" }
if (-not $TeacherUser) { $TeacherUser = "teacher" }
if (-not $StudentUser) { $StudentUser = "student" }

function Require-Param {
    param([string]$Value, [string]$Name)
    if (-not $Value) {
        Write-Host "Falta $Name. Ver el encabezado del script." -ForegroundColor Red
        exit 1
    }
}
Require-Param $KcAdminUser "KEYCLOAK_ADMIN"
Require-Param $KcAdminPass "KEYCLOAK_ADMIN_PASSWORD"
Require-Param $AdminPass   "SIGA_ADMIN_PASS"
Require-Param $TeacherPass "SIGA_TEACHER_PASS"
Require-Param $StudentPass "SIGA_STUDENT_PASS"
Require-Param $SimPassword "SIGA_SIM_PASS"

$created = 0; $reused = 0; $failed = 0
function ok   { param($m) Write-Host "   + $m" -ForegroundColor DarkGreen; $script:created++ }
function skip { param($m) Write-Host "   = $m" -ForegroundColor DarkGray;  $script:reused++ }
function bad  { param($m) Write-Host "   ! $m" -ForegroundColor Red;       $script:failed++ }
function banner {
    param($m)
    Write-Host ""
    Write-Host ("== " + $m + " " + ("=" * [math]::Max(3, 62 - $m.Length))) -ForegroundColor Cyan
}

# ---------------------------------------------------------------
# HTTP
# ---------------------------------------------------------------

function Get-Token {
    param([string]$TokenRealm, [string]$Client, [string]$User, [string]$Pass)
    $form = @{ grant_type = "password"; client_id = $Client; username = $User; password = $Pass }
    try {
        return (Invoke-RestMethod -Method Post -Body $form `
            -Uri "$KeycloakUrl/realms/$TokenRealm/protocol/openid-connect/token").access_token
    } catch {
        return $null
    }
}

function Try-Rest {
    param([string]$Method, [string]$Uri, $Headers, $Body, [string]$RawBody)

    $reqArgs = @{ Method = $Method; Uri = $Uri; Headers = $Headers; ErrorAction = "Stop" }
    # ConvertTo-Json colapsa un array de un elemento en objeto: los cuerpos que
    # tienen que viajar como lista (bloques de horario) van ya serializados.
    if ($RawBody) {
        $reqArgs["Body"] = $RawBody; $reqArgs["ContentType"] = "application/json"
    } elseif ($null -ne $Body) {
        $reqArgs["Body"] = ($Body | ConvertTo-Json -Compress -Depth 8)
        $reqArgs["ContentType"] = "application/json"
    }
    try {
        return @{ ok = $true; data = (Invoke-RestMethod @reqArgs); status = 200; message = "" }
    } catch {
        $status = 0; $msg = $_.Exception.Message
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode.value__
            try {
                $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
                $raw = $reader.ReadToEnd()
                if ($raw) {
                    $parsed = $raw | ConvertFrom-Json
                    if ($parsed.message) { $msg = $parsed.message }
                }
            } catch { }
        }
        return @{ ok = $false; data = $null; status = $status; message = $msg }
    }
}

# ---------------------------------------------------------------
# Tokens y cabeceras
# ---------------------------------------------------------------

Write-Host "Autenticando..." -ForegroundColor Cyan
$kcToken = Get-Token "master" "admin-cli" $KcAdminUser $KcAdminPass
if (-not $kcToken) { Write-Host "No se pudo entrar a Keycloak como $KcAdminUser." -ForegroundColor Red; exit 1 }

$adminToken = Get-Token $Realm $ClientId $AdminUser $AdminPass
if (-not $adminToken) { Write-Host "No se pudo entrar a SIGA como $AdminUser." -ForegroundColor Red; exit 1 }

$kcHeaders = @{ Authorization = "Bearer $kcToken" }
$adminApi  = "$KeycloakUrl/admin/realms/$Realm"
$H         = @{ Authorization = "Bearer $adminToken" }   # cabecera del ADMIN
Write-Host "  ADMIN de Keycloak y ADMIN de SIGA listos" -ForegroundColor DarkGray

# El realm emite tokens de 5 minutos (accessTokenLifespan=300) y este script
# hace unos 400 llamados. En local la corrida entera dura menos que eso y no se
# nota; contra una instancia remota dura varios minutos y los tokens pedidos al
# principio vencen a mitad de camino. El sintoma es enganoso: el backend
# responde 401, el script lo cuenta como "fallido" y parece un rechazo de regla
# de negocio. Peor aun, si el que vence es el token del ADMIN, la matricula
# falla en silencio y los estudiantes siguientes se quedan sin oferta
# disponible, asi que no se inscriben y el periodo se cierra sin ellos.
# Por eso cada token se renueva en cuanto pasa de los 4 minutos.
$TOKEN_MAX_AGE = 240

# Tokens por usuario, en cache: cada estudiante inscribe con el suyo y cada
# docente califica con el suyo, porque el backend saca la identidad del token.
$tokenCache = @{}
function User-Headers {
    param([string]$Username, [string]$Password)
    $entry = $tokenCache[$Username]
    if ($entry -and ((Get-Date) - $entry.at).TotalSeconds -lt $TOKEN_MAX_AGE) {
        return @{ Authorization = "Bearer $($entry.token)" }
    }
    $t = Get-Token $Realm $ClientId $Username $Password
    if (-not $t) { return $null }
    $tokenCache[$Username] = @{ token = $t; at = (Get-Date) }
    return @{ Authorization = "Bearer $t" }
}

# $H y $kcHeaders se mutan EN SITIO (no se reasignan) porque son hashtables que
# ya viajaron por referencia a decenas de llamadas: cambiarles la clave renueva
# la credencial en todas a la vez.
$adminTokenAt = Get-Date
$kcTokenAt    = Get-Date

function Refresh-Admin {
    if (((Get-Date) - $script:adminTokenAt).TotalSeconds -lt $TOKEN_MAX_AGE) { return }
    $t = Get-Token $Realm $ClientId $AdminUser $AdminPass
    if ($t) {
        $script:H["Authorization"] = "Bearer $t"
        $script:adminTokenAt = Get-Date
    }
}

function Refresh-Kc {
    # El realm master da tokens mas cortos todavia, asi que se renueva igual.
    if (((Get-Date) - $script:kcTokenAt).TotalSeconds -lt $TOKEN_MAX_AGE) { return }
    $t = Get-Token "master" "admin-cli" $KcAdminUser $KcAdminPass
    if ($t) {
        $script:kcHeaders["Authorization"] = "Bearer $t"
        $script:kcTokenAt = Get-Date
    }
}

# ---------------------------------------------------------------
# Keycloak: usuarios
# ---------------------------------------------------------------

$roleIds = @{}
foreach ($r in @("ADMIN", "TEACHER", "STUDENT")) {
    $role = (Try-Rest GET "$adminApi/roles/$r" $kcHeaders $null).data
    if (-not $role) { Write-Host "El realm no tiene el rol $r." -ForegroundColor Red; exit 1 }
    $roleIds[$r] = $role.id
}

function Get-KcUser {
    param([string]$Username)
    $res = Try-Rest GET "$adminApi/users?username=$Username&exact=true" $kcHeaders $null
    if ($res.ok) { return @($res.data) | Select-Object -First 1 }
    return $null
}

# Crea el usuario en Keycloak si no existe y le deja el rol del realm.
# Devuelve $true si quedo utilizable.
function Ensure-KcUser {
    param([string]$Username, [string]$Email, [string]$FullName, [string]$Role)

    Refresh-Kc
    $parts = $FullName.Split(" ")
    $user = Get-KcUser $Username
    if (-not $user) {
        $res = Try-Rest POST "$adminApi/users" $kcHeaders @{
            username      = $Username
            email         = $Email
            firstName     = $parts[0]
            lastName      = (($parts | Select-Object -Skip 1) -join " ")
            enabled       = $true
            emailVerified = $true
            credentials   = @(@{ type = "password"; value = $SimPassword; temporary = $false })
        }
        if (-not $res.ok) { return $false }
        $user = Get-KcUser $Username
        if (-not $user) { return $false }
    }
    # Asignar el rol es idempotente: repetirlo no cambia nada.
    $roleBody = "[" + (@{ id = $roleIds[$Role]; name = $Role } | ConvertTo-Json -Compress) + "]"
    Try-Rest POST "$adminApi/users/$($user.id)/role-mappings/realm" $kcHeaders $null -RawBody $roleBody | Out-Null
    return $true
}

# ---------------------------------------------------------------
# Catalogo: carreras, planes y asignaturas
# ---------------------------------------------------------------

banner "Catalogo"

$careers = @((Try-Rest GET "$BaseUrl/api/careers" $H $null).data)
if ($careers.Count -eq 0) {
    Write-Host "No hay carreras. Corre antes deploy/seed.sh." -ForegroundColor Red
    exit 1
}

# Por cada carrera: su plan VIGENTE y las asignaturas agrupadas por semestre.
$plans = @{}
foreach ($c in $careers) {
    $cPlans = @((Try-Rest GET "$BaseUrl/api/study-plans/career/$($c.id)" $H $null).data)
    $plan = @($cPlans) | Where-Object { $_.status -eq "VIGENTE" } | Select-Object -First 1
    if (-not $plan) { bad "la carrera $($c.code) no tiene plan VIGENTE"; continue }

    $subjects = @((Try-Rest GET "$BaseUrl/api/subjects/study-plan/$($plan.id)" $H $null).data)
    $bySem = @{}
    foreach ($s in $subjects) {
        # La clave va casteada a Int32 a proposito: ConvertFrom-Json puede
        # devolver Int64 y en una hashtable [long]1 no es la misma clave que
        # [int]1, asi que $bySem[1] volveria vacio.
        $sem = [int]$s.semester
        if (-not $bySem.ContainsKey($sem)) { $bySem[$sem] = New-Object System.Collections.ArrayList }
        [void]$bySem[$sem].Add($s)
    }
    foreach ($k in @($bySem.Keys)) {
        $bySem[$k] = @($bySem[$k] | Sort-Object code)
    }
    $plans[$c.code] = @{ career = $c; plan = $plan; bySemester = $bySem }
    Write-Host "   $($c.code) $($c.name) -> plan $($plan.identifier), $($subjects.Count) asignaturas" -ForegroundColor DarkGray
}

$careerCodes = @($plans.Keys | Sort-Object)

# ---------------------------------------------------------------
# Docentes
# ---------------------------------------------------------------

banner "Docentes"

# Tres docentes nuevos por carrera: con los que ya existen quedan 4 por plan,
# que es justo lo que necesita una oferta de 12 secciones por plan.
$newTeachers = @(
    @{ run = "19000001-1"; name = "Claudio Herrera Mena";    mail = "claudio.herrera";  title = "Ingeniero en Informatica";        degree = "Magister en Ingenieria de Software"; career = "10450" },
    @{ run = "19000002-2"; name = "Paulina Alvarez Soto";     mail = "paulina.alvarez";  title = "Ingeniera en Computacion";        degree = "Magister en Ciencias de la Computacion"; career = "10450" },
    @{ run = "19000003-3"; name = "Rodrigo Pizarro Lillo";    mail = "rodrigo.pizarro";  title = "Analista Programador";            degree = "Licenciado en Informatica"; career = "10450" },
    @{ run = "19000004-4"; name = "Veronica Munoz Gaete";     mail = "veronica.munoz";   title = "Ingeniera en Estadistica";        degree = "Magister en Ciencia de Datos"; career = "10451" },
    @{ run = "19000005-5"; name = "Hector Cardenas Rojas";    mail = "hector.cardenas";  title = "Ingeniero Civil Matematico";      degree = "Doctor en Estadistica"; career = "10451" },
    @{ run = "19000006-6"; name = "Carolina Bustos Pena";     mail = "carolina.bustos";  title = "Ingeniera en Informatica";        degree = "Magister en Analitica de Negocios"; career = "10451" },
    @{ run = "19000007-7"; name = "Esteban Valdivia Ortiz";   mail = "esteban.valdivia"; title = "Ingeniero en Redes y Seguridad";  degree = "Magister en Ciberseguridad"; career = "10452" },
    @{ run = "19000008-8"; name = "Macarena Gallardo Lira";   mail = "macarena.gallardo";title = "Ingeniera Civil en Informatica";  degree = "Magister en Seguridad de la Informacion"; career = "10452" },
    @{ run = "19000009-9"; name = "Nelson Quiroz Abarca";     mail = "nelson.quiroz";    title = "Ingeniero en Conectividad y Redes"; degree = "Diplomado en Etica Hacker"; career = "10452" }
)

$teachersByEmail = @{}
foreach ($t in @((Try-Rest GET "$BaseUrl/api/teachers" $H $null).data)) {
    $teachersByEmail[$t.email.ToLower()] = $t
}

foreach ($t in $newTeachers) {
    $email = "$($t.mail)@siga.cl"
    $existing = $teachersByEmail[$email.ToLower()]
    if ($existing) {
        skip "docente $($t.name)"
    } else {
        $res = Try-Rest POST "$BaseUrl/api/teachers" $H @{
            run               = $t.run
            fullName          = $t.name
            email             = $email
            professionalTitle = $t.title
            academicDegree    = $t.degree
        }
        if (-not $res.ok) { bad "docente $($t.name): HTTP $($res.status) $($res.message)"; continue }
        $teachersByEmail[$email.ToLower()] = $res.data
        ok "docente $($t.name) ($($t.career))"
    }
}

# Todo docente ACTIVO tiene que poder entrar: es el unico que puede calificar
# sus secciones. Los que venian de seed.sh no tenian usuario en Keycloak.
$teacherLogins = @{}
foreach ($t in @((Try-Rest GET "$BaseUrl/api/teachers" $H $null).data)) {
    $teachersByEmail[$t.email.ToLower()] = $t
    if ($t.status -ne "ACTIVO") { continue }

    $username = $t.email.Split("@")[0]
    if ($t.email -eq "teacher@siga.cl") {
        # El docente de la demo ya existe en el realm con su propia password.
        $teacherLogins[$t.id] = @{ user = $TeacherUser; pass = $TeacherPass }
        continue
    }
    if (Ensure-KcUser $username $t.email $t.fullName "TEACHER") {
        $teacherLogins[$t.id] = @{ user = $username; pass = $SimPassword }
    } else {
        bad "no se pudo preparar el login de $($t.fullName)"
    }
}
Write-Host "   $($teacherLogins.Count) docentes activos con login" -ForegroundColor DarkGray

# Pool de docentes por carrera, para repartir la oferta sin que nadie choque
# consigo mismo: el reparto real lo valida el backend, esto solo lo ordena.
$teacherPool = @{}
foreach ($code in $careerCodes) { $teacherPool[$code] = New-Object System.Collections.ArrayList }
$seedPool = @{
    "10450" = @("teacher@siga.cl", "claudio.herrera@siga.cl", "paulina.alvarez@siga.cl", "rodrigo.pizarro@siga.cl")
    "10451" = @("marta.diaz@siga.cl", "veronica.munoz@siga.cl", "hector.cardenas@siga.cl", "carolina.bustos@siga.cl")
    "10452" = @("luis.rojas@siga.cl", "esteban.valdivia@siga.cl", "macarena.gallardo@siga.cl", "nelson.quiroz@siga.cl")
}
foreach ($code in $careerCodes) {
    foreach ($mail in $seedPool[$code]) {
        $t = $teachersByEmail[$mail.ToLower()]
        if ($t -and $t.status -eq "ACTIVO") { [void]$teacherPool[$code].Add($t) }
    }
    if ($teacherPool[$code].Count -eq 0) {
        # Sin pool declarado: cualquiera activo sirve.
        foreach ($t in $teachersByEmail.Values) {
            if ($t.status -eq "ACTIVO") { [void]$teacherPool[$code].Add($t) }
        }
    }
}

# ---------------------------------------------------------------
# Estudiantes
# ---------------------------------------------------------------

banner "Estudiantes"

# Cohorte A: entra en 2025-1, en 2026-1 va en segundo semestre.
# Cohorte B: entra en 2026-1, primer semestre.
$cohortA = @(
    @{ run = "21000001-1"; name = "Matias Fuentes Lagos";    mail = "matias.fuentes";    career = "10450"; k = 1 },
    @{ run = "21000002-2"; name = "Javiera Morales Pinto";    mail = "javiera.morales";   career = "10450"; k = 2 },
    @{ run = "21000003-3"; name = "Benjamin Carrasco Soto";   mail = "benjamin.carrasco"; career = "10450"; k = 3 },
    @{ run = "21000004-4"; name = "Antonia Reyes Vidal";      mail = "antonia.reyes";     career = "10450"; k = 4 },
    @{ run = "21000005-5"; name = "Cristobal Nunez Salas";    mail = "cristobal.nunez";   career = "10450"; k = 5 },
    @{ run = "21000006-6"; name = "Fernanda Araya Godoy";     mail = "fernanda.araya";    career = "10451"; k = 1 },
    @{ run = "21000007-7"; name = "Ignacio Bravo Miranda";    mail = "ignacio.bravo";     career = "10451"; k = 2 },
    @{ run = "21000008-8"; name = "Catalina Herrera Pardo";   mail = "catalina.herrera";  career = "10451"; k = 3 },
    @{ run = "21000009-9"; name = "Sebastian Aguilar Rojas";  mail = "sebastian.aguilar"; career = "10451"; k = 4 },
    @{ run = "21000010-0"; name = "Francisca Leiva Mena";     mail = "francisca.leiva";   career = "10451"; k = 5 },
    @{ run = "21000011-1"; name = "Nicolas Tapia Cortes";     mail = "nicolas.tapia";     career = "10452"; k = 1 },
    @{ run = "21000012-2"; name = "Daniela Orellana Vega";    mail = "daniela.orellana";  career = "10452"; k = 2 },
    @{ run = "21000013-3"; name = "Felipe Sandoval Ruiz";     mail = "felipe.sandoval";   career = "10452"; k = 3 },
    @{ run = "21000014-4"; name = "Paula Guzman Alarcon";     mail = "paula.guzman";      career = "10452"; k = 4 },
    @{ run = "21000015-5"; name = "Rodrigo Molina Cabrera";   mail = "rodrigo.molina";    career = "10452"; k = 5 }
)
$cohortB = @(
    @{ run = "22000001-1"; name = "Emilia Castillo Vera";     mail = "emilia.castillo";   career = "10450" },
    @{ run = "22000002-2"; name = "Vicente Munoz Toro";       mail = "vicente.munoz";     career = "10450" },
    @{ run = "22000003-3"; name = "Josefa Silva Mella";       mail = "josefa.silva";      career = "10450" },
    @{ run = "22000004-4"; name = "Agustin Vargas Nieto";     mail = "agustin.vargas";    career = "10451" },
    @{ run = "22000005-5"; name = "Isidora Pena Rivas";       mail = "isidora.pena";      career = "10451" },
    @{ run = "22000006-6"; name = "Tomas Espinoza Lara";      mail = "tomas.espinoza";    career = "10451" },
    @{ run = "22000007-7"; name = "Amanda Figueroa Soto";     mail = "amanda.figueroa";   career = "10452" },
    @{ run = "22000008-8"; name = "Maximiliano Ortega Diaz";  mail = "maximiliano.ortega";career = "10452" },
    @{ run = "22000009-9"; name = "Trinidad Valdes Nunez";    mail = "trinidad.valdes";   career = "10452" }
)

$studentsByEmail = @{}
foreach ($s in @((Try-Rest GET "$BaseUrl/api/students" $H $null).data)) {
    $studentsByEmail[$s.email.ToLower()] = $s
}

function Ensure-Student {
    param($Spec)
    Refresh-Admin
    $email = "$($Spec.mail)@siga.cl"
    $existing = $studentsByEmail[$email.ToLower()]
    if ($existing) { skip "estudiante $($Spec.name)"; return $existing }

    $careerId = $plans[$Spec.career].career.id
    $res = Try-Rest POST "$BaseUrl/api/students" $H @{
        run = $Spec.run; fullName = $Spec.name; email = $email; careerId = $careerId
    }
    if (-not $res.ok) { bad "estudiante $($Spec.name): HTTP $($res.status) $($res.message)"; return $null }
    $studentsByEmail[$email.ToLower()] = $res.data
    ok "estudiante $($Spec.name) ($($Spec.career))"
    return $res.data
}

foreach ($s in $cohortA) { [void](Ensure-Student $s) }
foreach ($s in $cohortB) { [void](Ensure-Student $s) }

# Login para todo estudiante: sin token propio no puede inscribir, porque el
# backend saca su id del claim email y nunca del cuerpo.
$studentLogins = @{}
foreach ($s in @((Try-Rest GET "$BaseUrl/api/students" $H $null).data)) {
    $studentsByEmail[$s.email.ToLower()] = $s
    $username = $s.email.Split("@")[0]
    if ($s.email -eq "student@siga.cl") {
        $studentLogins[$s.id] = @{ user = $StudentUser; pass = $StudentPass }
        continue
    }
    if ($s.email -like "*@perf.siga.cl") { continue }   # los de carga no son parte de la demo
    if (Ensure-KcUser $username $s.email $s.fullName "STUDENT") {
        $studentLogins[$s.id] = @{ user = $username; pass = $SimPassword }
    } else {
        bad "no se pudo preparar el login de $($s.fullName)"
    }
}
Write-Host "   $($studentLogins.Count) estudiantes con login" -ForegroundColor DarkGray

# ---------------------------------------------------------------
# Horario: 5 dias x 6 modulos = 30 bloques posibles
# ---------------------------------------------------------------

# El orden recorre primero el modulo y despues el dia, asi una asignatura de
# tres bloques cae en tres dias distintos a la misma hora, como un horario real.
$slotOrder = New-Object System.Collections.ArrayList
foreach ($m in 1..6) { foreach ($d in @("L","M","W","J","V")) { [void]$slotOrder.Add("$d-$m") } }

# Lo que ya esta ocupado, para no pelear con lo que creo seed.sh:
#   planSlots[periodo-plan]  -> todas las secciones de un plan en un periodo van
#                               en bloques distintos, asi el horario de cualquier
#                               cohorte (y de un repitente que mezcla semestres)
#                               no choca nunca.
#   teacherSlots[periodo-id] -> un docente no puede estar en dos salas a la vez.
$planSlots    = @{}
$teacherSlots = @{}

# Solo garantiza la entrada; el llamador indexa la tabla. No devuelve el
# HashSet a proposito: PowerShell ENUMERA las colecciones que salen de una
# funcion, asi que un conjunto vacio volveria como $null y uno con elementos
# volveria convertido en array (sin .Add()).
function Ensure-SlotSet {
    param($Table, [string]$Key)
    if (-not $Table.ContainsKey($Key)) {
        $Table[$Key] = New-Object System.Collections.Generic.HashSet[string]
    }
}

function Register-Section {
    param([long]$PeriodId, [string]$CareerCode, [long]$TeacherId, $Blocks)
    $planKey    = "$PeriodId-$CareerCode"
    $teacherKey = "$PeriodId-$TeacherId"
    Ensure-SlotSet $planSlots    $planKey
    Ensure-SlotSet $teacherSlots $teacherKey
    foreach ($b in $Blocks) {
        [void]$planSlots[$planKey].Add($b)
        [void]$teacherSlots[$teacherKey].Add($b)
    }
}

function Pick-Slots {
    param([int]$Needed, [string]$PeriodId, [string]$CareerCode, [long]$TeacherId)
    $planKey    = "$PeriodId-$CareerCode"
    $teacherKey = "$PeriodId-$TeacherId"
    Ensure-SlotSet $planSlots    $planKey
    Ensure-SlotSet $teacherSlots $teacherKey
    $taken = $planSlots[$planKey]
    $mine  = $teacherSlots[$teacherKey]
    $out = New-Object System.Collections.ArrayList
    foreach ($s in $slotOrder) {
        if ($out.Count -ge $Needed) { break }
        if ($taken.Contains($s)) { continue }
        if ($mine.Contains($s))  { continue }
        [void]$out.Add($s)
    }
    if ($out.Count -lt $Needed) { return $null }
    return @($out)
}

# Carga en las tablas de ocupacion lo que ya existe en un periodo.
function Load-Existing-Sections {
    param([long]$PeriodId)
    $sections = @((Try-Rest GET "$BaseUrl/api/sections?academicPeriodId=$PeriodId" $H $null).data)
    $byCode = @{}
    foreach ($sec in $sections) {
        $blocks = @($sec.scheduleBlocks | ForEach-Object { "$($_.day)-$($_.module)" })
        $careerCode = $null
        foreach ($code in $careerCodes) {
            foreach ($sem in $plans[$code].bySemester.Keys) {
                if (@($plans[$code].bySemester[$sem] | Where-Object { $_.code -eq $sec.subjectCode }).Count -gt 0) {
                    $careerCode = $code
                }
            }
        }
        if ($careerCode) { Register-Section $PeriodId $careerCode $sec.teacherId $blocks }
        $byCode[$sec.subjectCode] = $sec
    }
    return $byCode
}

# Crea la seccion de una asignatura si no existe en el periodo.
function Ensure-Section {
    param([long]$PeriodId, [string]$CareerCode, $Subject, $Teacher, [int]$Capacity, $Existing)

    if ($Existing.ContainsKey($Subject.code)) { return $Existing[$Subject.code] }
    Refresh-Admin

    $needed = [int](($Subject.telTheory + $Subject.telExercises + $Subject.telLab) / 2)
    $slots = Pick-Slots $needed "$PeriodId" $CareerCode $Teacher.id
    if (-not $slots) {
        bad "seccion $($Subject.code): no quedan $needed bloques libres en el plan $CareerCode"
        return $null
    }

    $blockJson = ($slots | ForEach-Object {
        $p = $_.Split("-")
        '{"day":"' + $p[0] + '","module":' + $p[1] + '}'
    }) -join ","
    $raw = '{"subjectId":' + $Subject.id + ',"academicPeriodId":' + $PeriodId +
           ',"teacherId":' + $Teacher.id + ',"capacity":' + $Capacity +
           ',"scheduleBlocks":[' + $blockJson + ']}'

    $res = Try-Rest POST "$BaseUrl/api/sections" $H $null -RawBody $raw
    if (-not $res.ok) {
        bad "seccion $($Subject.code): HTTP $($res.status) $($res.message)"
        return $null
    }
    Register-Section $PeriodId $CareerCode $Teacher.id $slots
    $Existing[$Subject.code] = $res.data
    ok "seccion $($Subject.code) - $($Teacher.fullName) [$($slots -join ' ')]"
    return $res.data
}

# Reparte las secciones de un plan entre SU pool de docentes. seed.sh dejo a
# Marta y a Luis dictando en dos carreras a la vez, con 6 secciones cada uno
# mientras otros tenian 1. Reasignar es seguro porque todas las secciones de un
# plan en un periodo tienen bloques distintos entre si: un docente que solo
# dicta en ese plan no puede chocar consigo mismo.
function Rebalance-Teachers {
    param([long]$PeriodId)

    Refresh-Admin
    $sections = @((Try-Rest GET "$BaseUrl/api/sections?academicPeriodId=$PeriodId" $H $null).data)
    $load = @{}
    foreach ($sec in $sections) {
        if (-not $load.ContainsKey($sec.teacherId)) { $load[$sec.teacherId] = 0 }
        $load[$sec.teacherId]++
    }

    $moved = 0
    foreach ($code in $careerCodes) {
        $pool = @($teacherPool[$code])
        $poolIds = @($pool | ForEach-Object { $_.id })
        $codes = @()
        foreach ($sem in @($plans[$code].bySemester.Keys)) {
            $codes += @($plans[$code].bySemester[$sem] | ForEach-Object { $_.code })
        }

        foreach ($sec in @($sections | Where-Object { $codes -contains $_.subjectCode })) {
            if ($poolIds -contains $sec.teacherId) { continue }

            # El del pool con menos carga.
            $target = $null; $best = 999
            foreach ($t in $pool) {
                $n = 0
                if ($load.ContainsKey($t.id)) { $n = $load[$t.id] }
                if ($n -lt $best) { $best = $n; $target = $t }
            }
            if (-not $target) { continue }

            $blockJson = ($sec.scheduleBlocks | ForEach-Object {
                '{"day":"' + $_.day + '","module":' + $_.module + '}'
            }) -join ","
            $raw = '{"subjectId":' + $sec.subjectId + ',"academicPeriodId":' + $PeriodId +
                   ',"teacherId":' + $target.id + ',"capacity":' + $sec.capacity +
                   ',"scheduleBlocks":[' + $blockJson + ']}'

            $res = Try-Rest PUT "$BaseUrl/api/sections/$($sec.id)" $H $null -RawBody $raw
            if ($res.ok) {
                $load[$sec.teacherId]--
                if (-not $load.ContainsKey($target.id)) { $load[$target.id] = 0 }
                $load[$target.id]++
                $moved++
                ok "seccion $($sec.subjectCode) reasignada a $($target.fullName)"
            } else {
                bad "reasignar $($sec.subjectCode): HTTP $($res.status) $($res.message)"
            }
        }
    }
    if ($moved -eq 0) { skip "la carga docente ya estaba repartida por plan" }
}

# ---------------------------------------------------------------
# Inscripcion y notas
# ---------------------------------------------------------------

# Inscribe al estudiante con SU token. Toma el prefijo de la oferta ordenada por
# (semestre, codigo): eso cumple sola la regla de "semestres inferiores
# primero", porque nunca deja una candidata de semestre menor sin seleccionar.
function Enroll-Student {
    param($Student, [long]$PeriodId, [int]$Max = 6)

    $login = $studentLogins[$Student.id]
    if (-not $login) { bad "sin login: $($Student.fullName)"; return 0 }
    $headers = User-Headers $login.user $login.pass
    if (-not $headers) { bad "sin token: $($Student.fullName)"; return 0 }

    # Cuanto le cabe todavia: el tope de 6 cuenta lo YA inscrito, asi que sin
    # este descuento la segunda pasada del script pide un septimo ramo y el
    # backend la rechaza con razon.
    $mine = @((Try-Rest GET "$BaseUrl/api/enrollments/me" $headers $null).data)
    $already = @($mine | Where-Object {
        $_.academicPeriodId -eq $PeriodId -and $_.status -eq "INSCRITA"
    }).Count
    $room = $Max - $already
    if ($room -le 0) { return 0 }

    $avail = @((Try-Rest GET "$BaseUrl/api/enrollments/available?academicPeriodId=$PeriodId" $headers $null).data)
    if ($avail.Count -eq 0) { return 0 }

    $sorted = @($avail | Sort-Object subjectSemester, subjectCode)
    $take = [math]::Min($room, $sorted.Count)
    $ids = @($sorted[0..($take - 1)] | ForEach-Object { $_.sectionId })

    $raw = '{"academicPeriodId":' + $PeriodId + ',"sectionIds":[' + ($ids -join ",") + ']}'
    $res = Try-Rest POST "$BaseUrl/api/enrollments" $headers $null -RawBody $raw
    if (-not $res.ok) {
        bad "inscripcion de $($Student.fullName): HTTP $($res.status) $($res.message)"
        return 0
    }
    return @($res.data).Count
}

# Nota determinista: la misma entrada da siempre la misma nota, asi que volver a
# correr el script no cambia el historial.
function Grade-Value {
    param([int]$Seed, [bool]$Pass)
    if ($Pass) { $v = 4.0 + ((($Seed * 7) % 26) / 10.0) }   # 4.0 .. 6.5
    else       { $v = 2.0 + ((($Seed * 3) % 18) / 10.0) }   # 2.0 .. 3.7
    return [math]::Round($v, 1)
}

function Code-Seed {
    param([string]$Text)
    $sum = 0
    foreach ($ch in $Text.ToCharArray()) { $sum += [int]$ch }
    return $sum
}

# Califica una seccion con el token de su docente responsable.
#   Mode = full     -> todas las inscripciones
#          partial  -> la mitad
#          none     -> ninguna
# $FailCodes mapea studentId -> lista de codigos que ese estudiante reprueba.
function Grade-Section {
    param($Section, [string]$Mode, $FailCodes)

    if ($Mode -eq "none") { return 0 }
    $login = $teacherLogins[$Section.teacherId]
    if (-not $login) { bad "sin login el docente de $($Section.subjectCode)"; return 0 }
    $headers = User-Headers $login.user $login.pass
    if (-not $headers) { bad "sin token el docente de $($Section.subjectCode)"; return 0 }

    $sheet = @((Try-Rest GET "$BaseUrl/api/grades/section/$($Section.id)" $headers $null).data)
    if ($sheet.Count -eq 0) { return 0 }

    # El corte del parcial se calcula sobre la planilla COMPLETA ordenada por
    # nombre, no sobre lo que falta: si no, cada pasada del script calificaria
    # la mitad del resto y la seccion terminaria cerrada sola.
    $roster = @($sheet | Sort-Object studentFullName)
    $take = $roster.Count
    if ($Mode -eq "partial") { $take = [int][math]::Max(1, [math]::Floor($roster.Count / 2)) }

    $target = @($roster[0..($take - 1)] | Where-Object { $null -eq $_.value })
    if ($target.Count -eq 0) { return 0 }

    $done = 0
    foreach ($row in $target) {
        $fails = @()
        if ($FailCodes -and $FailCodes.ContainsKey($row.studentId)) { $fails = @($FailCodes[$row.studentId]) }
        $pass = -not ($fails -contains $Section.subjectCode)

        $seed = (Code-Seed "$($row.studentId)$($Section.subjectCode)")
        $value = Grade-Value $seed $pass
        # El numero va a mano a la cadena JSON: con cultura es-CL, un double
        # serializado saldria con coma y el cuerpo no seria JSON valido.
        $valueText = [string]::Format([System.Globalization.CultureInfo]::InvariantCulture, "{0:0.0}", $value)
        $raw = '{"enrollmentId":' + $row.enrollmentId + ',"value":' + $valueText + '}'

        $res = Try-Rest POST "$BaseUrl/api/grades" $headers $null -RawBody $raw
        if ($res.ok) { $done++ } else { bad "nota de $($row.studentFullName) en $($Section.subjectCode): HTTP $($res.status) $($res.message)" }
    }
    return $done
}

function Matriculate {
    param($Student, [long]$PeriodId)
    Refresh-Admin
    $res = Try-Rest PUT "$BaseUrl/api/student-period-status" $H @{
        studentId = $Student.id; academicPeriodId = $PeriodId; enrollmentStatus = "MATRICULATED"
    }
    if (-not $res.ok) { bad "matricula de $($Student.fullName): HTTP $($res.status) $($res.message)" }
    return $res.ok
}

function Ensure-Period {
    param([int]$Year, [int]$Semester)
    $code = "$Year-$Semester"
    $all = @((Try-Rest GET "$BaseUrl/api/academic-periods" $H $null).data)
    $found = @($all | Where-Object { $_.code -eq $code }) | Select-Object -First 1
    if ($found) { return $found }

    $res = Try-Rest POST "$BaseUrl/api/academic-periods" $H @{ year = $Year; semester = $Semester }
    if (-not $res.ok) { bad "periodo $code : HTTP $($res.status) $($res.message)"; return $null }
    ok "periodo $code creado (ABIERTO)"
    return $res.data
}

# ---------------------------------------------------------------
# 2025-1: el pasado, generado por el sistema
# ---------------------------------------------------------------

# Quien reprueba que, en 2025-1. El indice k es la posicion dentro de la
# cohorte de su carrera: los k=4 arrastran una asignatura y los k=5, dos.
$failPlan = @{}
foreach ($spec in $cohortA) {
    $email = "$($spec.mail)@siga.cl"
    $st = $studentsByEmail[$email.ToLower()]
    if (-not $st) { continue }
    $sem1 = @($plans[$spec.career].bySemester[1])
    if ($spec.k -eq 4) { $failPlan[$st.id] = @($sem1[4].code) }
    if ($spec.k -eq 5) { $failPlan[$st.id] = @($sem1[2].code, $sem1[5].code) }
}

if (-not $SkipPast) {
    banner "2025-1: primer periodo (se cierra al final)"

    $past = Ensure-Period 2025 1
    if ($past -and $past.status -eq "CERRADO") {
        skip "2025-1 ya esta cerrado: el historial ya existe"
    } elseif ($past) {
        $existing = Load-Existing-Sections $past.id

        # Oferta: el semestre 1 de los tres planes.
        foreach ($code in $careerCodes) {
            $pool = @($teacherPool[$code])
            $idx = 0
            foreach ($subject in @($plans[$code].bySemester[1])) {
                $teacher = $pool[$idx % $pool.Count]
                [void](Ensure-Section $past.id $code $subject $teacher 40 $existing)
                $idx++
            }
        }

        # Matricula e inscripcion de la cohorte A.
        $enrolled = 0
        foreach ($spec in $cohortA) {
            $st = $studentsByEmail["$($spec.mail)@siga.cl".ToLower()]
            if (-not $st) { continue }
            if (-not (Matriculate $st $past.id)) { continue }   # Matriculate ya reporto el fallo
            $n = Enroll-Student $st $past.id 6
            $enrolled += $n
        }
        Write-Host "   $enrolled inscripciones en 2025-1" -ForegroundColor DarkGray

        # Notas: todas, porque sin todas el cierre no pasa (y es justo lo que
        # queremos demostrar que el sistema exige).
        $graded = 0
        foreach ($sec in @((Try-Rest GET "$BaseUrl/api/sections?academicPeriodId=$($past.id)" $H $null).data)) {
            $graded += Grade-Section $sec "full" $failPlan
        }
        Write-Host "   $graded notas puestas en 2025-1" -ForegroundColor DarkGray

        # Cierre: el sistema copia todo al historial y decide ELIMINADO/EGRESADO.
        $ready = (Try-Rest GET "$BaseUrl/api/academic-periods/$($past.id)/closure-readiness" $H $null).data
        if ($ready) {
            Write-Host "   readiness: canClose=$($ready.canClose) inscritas=$($ready.enrolledCount) pendientes=$($ready.pendingCount)" -ForegroundColor DarkGray
        }
        $close = Try-Rest PATCH "$BaseUrl/api/academic-periods/$($past.id)/status?status=CERRADO" $H $null
        if ($close.ok) { ok "2025-1 cerrado: el historial academico lo escribio el cierre" }
        else { bad "cierre de 2025-1: HTTP $($close.status) $($close.message)" }
    }
}

# ---------------------------------------------------------------
# 2026-1: el periodo abierto
# ---------------------------------------------------------------

banner "2026-1: periodo abierto"

$open = @((Try-Rest GET "$BaseUrl/api/academic-periods?status=ABIERTO" $H $null).data) |
        Sort-Object code -Descending | Select-Object -First 1
if (-not $open) {
    Write-Host "No hay periodo ABIERTO: nada que poblar." -ForegroundColor Red
    exit 1
}
Write-Host "   periodo $($open.code) (id $($open.id))" -ForegroundColor DarkGray

$existingOpen = Load-Existing-Sections $open.id

# Oferta: semestres 1 y 2 de los tres planes. Mas arriba no hay nadie todavia
# -- el instituto lleva dos periodos -- asi que ofrecer 3 y 4 seria decorado.
foreach ($code in $careerCodes) {
    $pool = @($teacherPool[$code])
    $idx = 0
    foreach ($sem in @(1, 2)) {
        foreach ($subject in @($plans[$code].bySemester[$sem])) {
            $teacher = $pool[$idx % $pool.Count]
            $capacity = if ($sem -eq 1) { 40 } else { 35 }
            [void](Ensure-Section $open.id $code $subject $teacher $capacity $existingOpen)
            $idx++
        }
    }
}

Rebalance-Teachers $open.id

# Matricula: todos los REGULAR. Valentina Soto queda fuera a proposito, porque
# esta en POSTERGACION y el sistema no la deja inscribir.
$allStudents = @((Try-Rest GET "$BaseUrl/api/students" $H $null).data)
$demoStudents = @($allStudents | Where-Object { $_.email -notlike "*@perf.siga.cl" })

$matriculated = 0
foreach ($st in $demoStudents) {
    if ($st.status -ne "REGULAR") { continue }
    if (Matriculate $st $open.id) { $matriculated++ }
}
Write-Host "   $matriculated matriculados en $($open.code)" -ForegroundColor DarkGray

$enrolled = 0
$enrolledStudents = 0
foreach ($st in $demoStudents) {
    if ($st.status -ne "REGULAR") { continue }
    $n = Enroll-Student $st $open.id 6
    if ($n -gt 0) { $enrolled += $n; $enrolledStudents++ }
}
Write-Host "   $enrolled inscripciones nuevas ($enrolledStudents estudiantes)" -ForegroundColor DarkGray

# Notas parciales: el ciclo full/partial/none deja a cada docente con una
# seccion cerrada, una a medias y una sin tocar. Asi el cierre tiene algo que
# reclamar y la planilla del docente muestra los tres estados.
$sectionsOpen = @((Try-Rest GET "$BaseUrl/api/sections?academicPeriodId=$($open.id)" $H $null).data | Sort-Object id)
$modes = @("full", "partial", "none")
$gradedOpen = 0
$i = 0
foreach ($sec in $sectionsOpen) {
    $gradedOpen += Grade-Section $sec $modes[$i % 3] $null
    $i++
}
Write-Host "   $gradedOpen notas puestas en $($open.code)" -ForegroundColor DarkGray

# ---------------------------------------------------------------
# El caso ELIMINADO, armado para el cierre
# ---------------------------------------------------------------

# La eliminacion exige la SEGUNDA reprobacion de la misma asignatura, asi que
# no puede salir de un solo periodo: hay que reprobar en 2025-1 y volver a
# reprobar lo mismo en 2026-1. Esta nota deja el caso montado -- al cerrar
# 2026-1, el sistema deja a esta estudiante ELIMINADO (y eso le gana a
# cualquier egreso, que es la parte que importa demostrar).
$secondFailure = @(
    @{ mail = "paula.guzman"; code = "TCS105" }
)

foreach ($case in $secondFailure) {
    $st = $studentsByEmail["$($case.mail)@siga.cl".ToLower()]
    if (-not $st) { continue }
    $sec = $existingOpen[$case.code]
    if (-not $sec) { bad "no existe la seccion $($case.code) en $($open.code)"; continue }

    $login = $teacherLogins[$sec.teacherId]
    if (-not $login) { bad "sin login el docente de $($case.code)"; continue }
    $headers = User-Headers $login.user $login.pass
    if (-not $headers) { bad "sin token el docente de $($case.code)"; continue }

    $sheet = @((Try-Rest GET "$BaseUrl/api/grades/section/$($sec.id)" $headers $null).data)
    $row = @($sheet | Where-Object { $_.studentId -eq $st.id }) | Select-Object -First 1
    if (-not $row) { bad "$($st.fullName) no esta inscrita en $($case.code)"; continue }

    $value = Grade-Value (Code-Seed "$($st.id)$($case.code)") $false
    $valueText = [string]::Format([System.Globalization.CultureInfo]::InvariantCulture, "{0:0.0}", $value)

    if ($null -eq $row.value) {
        $raw = '{"enrollmentId":' + $row.enrollmentId + ',"value":' + $valueText + '}'
        $res = Try-Rest POST "$BaseUrl/api/grades" $headers $null -RawBody $raw
    } elseif ([double]$row.value -ge 4.0) {
        # Ya estaba aprobada por el ciclo de notas: se corrige con el PUT.
        $raw = '{"enrollmentId":' + $row.enrollmentId + ',"value":' + $valueText + '}'
        $res = Try-Rest PUT "$BaseUrl/api/grades/$($row.id)" $headers $null -RawBody $raw
    } else {
        skip "segunda reprobacion de $($st.fullName) en $($case.code) ya estaba"
        continue
    }

    if ($res.ok) { ok "segunda reprobacion armada: $($st.fullName) $($case.code) = $valueText" }
    else { bad "segunda reprobacion de $($st.fullName): HTTP $($res.status) $($res.message)" }
}

# ---------------------------------------------------------------
# Resumen
# ---------------------------------------------------------------

Refresh-Admin
banner "Resumen"

foreach ($p in @((Try-Rest GET "$BaseUrl/api/academic-periods" $H $null).data | Sort-Object code)) {
    $secs = @((Try-Rest GET "$BaseUrl/api/sections?academicPeriodId=$($p.id)" $H $null).data)
    Write-Host ("   {0}  {1,-8}  {2,2} secciones" -f $p.code, $p.status, $secs.Count) -ForegroundColor White
}

$ready = (Try-Rest GET "$BaseUrl/api/academic-periods/$($open.id)/closure-readiness" $H $null).data
if ($ready) {
    Write-Host ""
    Write-Host "   Cierre de $($open.code): canClose=$($ready.canClose)  inscritas=$($ready.enrolledCount)  con nota=$($ready.gradedCount)  pendientes=$($ready.pendingCount)" -ForegroundColor White
    if ($ready.pendingSubjectCodes.Count -gt 0) {
        Write-Host "   Falta calificar: $($ready.pendingSubjectCodes -join ', ')" -ForegroundColor DarkGray
    }
}

Write-Host ""
Write-Host "   Cuentas creadas: usuario = parte local del email, password = la de SIGA_SIM_PASS" -ForegroundColor White
Write-Host "   Docentes activos con seccion en $($open.code):" -ForegroundColor DarkGray
foreach ($t in @((Try-Rest GET "$BaseUrl/api/teachers" $H $null).data | Sort-Object fullName)) {
    if ($t.status -ne "ACTIVO") { continue }
    $mine = @($sectionsOpen | Where-Object { $_.teacherId -eq $t.id })
    if ($mine.Count -eq 0) { continue }
    $login = $teacherLogins[$t.id]
    $user = if ($login) { $login.user } else { "(sin login)" }
    Write-Host ("     {0,-22} {1,-26} {2} secciones: {3}" -f $user, $t.fullName, $mine.Count, (($mine | ForEach-Object { $_.subjectCode }) -join " ")) -ForegroundColor DarkGray
}

Write-Host ""
Write-Host "   creados: $created   ya existian: $reused   fallidos: $failed" -ForegroundColor $(if ($failed -gt 0) { "Yellow" } else { "Green" })
if ($failed -gt 0) {
    Write-Host "   Los fallos de regla de negocio (409) suelen ser correctos: el sistema esta rechazando algo que no corresponde." -ForegroundColor DarkGray
}
