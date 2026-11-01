## Técnicas de Ingeniería de Software 

## Evaluación 1 – (2026 - 2) 

1. **Descripción del trabajo** 

Los estudiantes deberán desarrollar y desplegar individualmente una aplicación web monolítica basada en una arquitectura por capas. 

2. **Lineamientos generales** 

    - El proyecto y su evaluación serán de carácter individual. 
    - No se requerirá la entrega de un informe escrito. 
    - Cada estudiante deberá presentarse puntualmente en la fecha y hora asignadas para su evaluación. La inasistencia o atraso que impida realizar la evaluación será calificado con la nota mínima (1,0). 
    - Solo podrán presentarse a la evaluación los estudiantes programados para la fecha y horario correspondientes. No se permitirá rendir la evaluación en una fecha u horario diferente al asignado. 

3\. **Aspectos del desarrollo del producto** 

### 3.1 Respecto del Frontend 

| - | El frontend deberá ser desarrollado utilizando ReactJS con Vite.  |
| --- | --- |
| - | Los componentes de React deberán implementarse utilizando componentes funcionales y  |
|  | Hooks.  |
| - | La comunicación con el backend deberá realizarse utilizando Axios.  |
| - | La navegación entre las vistas deberá implementarse utilizando React Router.  |
| - | La aplicación deberá contar con un único frontend.  |
| - | La interfaz deberá utilizar una librería o framework de componentes/estilos, como  |

Bootstrap, Material UI (MUI), Tailwind CSS u otra alternativa equivalente. No se aceptarán interfaces desarrolladas únicamente con HTML y CSS básico. 

\- El código fuente deberá estar escrito en inglés. 

### 3.2 Respecto del Backend 

\- El backend deberá ser desarrollado en Java utilizando Spring Boot, siguiendo el paradigma de programación orientada a objetos y una arquitectura monolítica por capas. 

\- La arquitectura deberá considerar, al menos, las capas Controller, Service y Repository, además de las entidades del dominio. 

\- El backend deberá proporcionar una API REST mediante endpoints, utilizando apropiadamente los métodos HTTP (GET, POST, PUT, DELETE) y los códigos de estado HTTP correspondientes. 

\- La lógica de negocio deberá implementarse en la capa Service. - El acceso a la base de datos deberá realizarse mediante Spring Data JPA. 

| - | El proyecto deberá utilizar, al menos, Spring Web, Spring Data JPA, Lombok y el driver de la base de datos seleccionada. Para implementar la autenticación y autorización, deberá  |
| --- | --- |
| - | utilizar además Spring Security y OAuth2 Resource Server. La aplicación deberá utilizar una base de datos relacional, como MySQL o PostgreSQL.  |
| - | La API REST deberá estar documentada utilizando OpenAPI/Swagger.  |
| - | El código fuente deberá estar escrito en inglés.  |
| **3.3 Despliegue del producto en producción**  |  |
| - | La aplicación web (frontend y backend) deberá quedar desplegada y completamente funcional en un servidor en la nube (AWS, Azure, GCP, DigitalOcean u otro).  |
| - | El despliegue deberá realizarse manualmente utilizando Docker Compose e incluir los siguientes componentes: `o` Una instancia de base de datos (MySQL o PostgreSQL). `o` Tres réplicas del backend.  |
| - | `o` Una instancia de Nginx, utilizada como balanceador de carga de las tres réplicas del backend. `o` Una única instancia del frontend. Las imágenes Docker del frontend y backend deberán estar almacenadas en Docker Hub.  |

Para la Base de Datos y Nginx se podrán utilizar las imágenes oficiales disponibles en Docker Hub. 

\- La base de datos deberá utilizar un volumen Docker para garantizar la persistencia de los datos ante la detención o recreación del contenedor. 

\- Los componentes de la aplicación deberán ejecutarse en contenedores Docker y comunicarse mediante la red definida en Docker Compose. 

\- La configuración de la aplicación deberá realizarse mediante variables de entorno, evitando incorporar credenciales, contraseñas u otros datos sensibles directamente en el código fuente. 

\- El backend deberá desplegarse utilizando tres réplicas, y Nginx deberá actuar como balanceador de carga, distribuyendo las solicitudes entre dichas réplicas. 

\- La aplicación deberá quedar accesible y completamente operativa desde un navegador web. 

### 3.4 Desarrollo y testing del producto 

\- El código fuente del proyecto deberá mantenerse en un único repositorio de GitHub (monorepo), utilizando Git para el control de versiones. El repositorio deberá contener tanto el frontend como el backend de la aplicación. 

| - | Se deberá implementar un pipeline de _Continuous Delivery (CD)_ utilizando _GitHub Actions_,  |
| --- | --- |
|  | que automatice las siguientes tareas: `o` Obtener el código fuente del frontend y backend desde GitHub. `o` Ejecutar las pruebas unitarias del backend. `o` Ejecutar las pruebas de integración del backend.  |
| - | `o` Construir las imágenes Docker del frontend y backend. `o` Publicar las imágenes generadas en Docker Hub. La construcción y publicación de las imágenes Docker deberá realizarse únicamente si las  |
|  | pruebas unitarias y de integración finalizan correctamente.  |
| - | El pipeline finalizará con la publicación de las imágenes en Docker Hub. El despliegue en el  |

servidor se realizará manualmente mediante Docker Compose. 

| - | _Pruebas_  | _unitarias:_ | La  |  | capa  | de  | servicios  | (lógica  | de  | negocio)  | deberá  | contar  | con  | pruebas  |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
|  | unitarias y alcanzar una cobertura de líneas de código mayor o igual al 90%.  |  |  |  |  |  |  |  |  |  |  |  |  |  |
| - |  | _Pruebas de integración:_ |  |  Se deberán implementar pruebas de integración para al menos tres  |  |  |  |  |  |  |  |  |  |  |

procesos relevantes del sistema, considerando para cada proceso tres escenarios exitosos y tres escenarios de rechazo, totalizando al menos 18 casos de prueba. 

### 3.5 Autenticación y Autorización del sistema 

| - | El  | sistema deberá implementar autenticación y autorización mediante Keycloak, como  |
| --- | --- | --- |
|  | herramienta de gestión de identidades y accesos (IAM).  |  |
| - | La  | autenticación se realizará mediante usuario y contraseña. Una vez autenticado, el  |
|  | usuario podrá acceder al sistema de acuerdo con los permisos asociados a su rol.  |  |
| - | Se  | deberán considerar, al menos, los siguientes roles: ADMIN, TEACHER y STUDENT,  |

correspondientes a los actores Administrador Académico, Docente y Estudiante definidos en el sistema. 

\- El frontend deberá restringir el acceso a las vistas y funcionalidades de acuerdo con el rol del usuario autenticado. 

\- El backend deberá proteger los endpoints de la API REST, verificando que el usuario se encuentre autenticado y cuente con el rol requerido para ejecutar cada operación. 

\- El backend deberá utilizar Spring Security para proteger los endpoints de la API REST y validar los tokens de acceso JWT emitidos por Keycloak, aplicando las restricciones de acceso correspondientes a los roles definidos. 

\- La comunicación entre el frontend y el backend deberá utilizar el token de acceso (JWT) emitido por Keycloak. 

\- No se deberá implementar un mecanismo propio de autenticación ni almacenar las contraseñas de los usuarios en la base de datos de la aplicación. 

4\. **Acerca del proyecto de software** 

### 4.1 Contexto del problema 

El **Instituto Profesional de Tecnología (IPT)** es una institución de educación superior que imparte carreras técnicas de dos años de duración, organizadas en cuatro semestres académicos. 

El instituto imparte tres carreras y cuenta actualmente con un total de 315 estudiantes y 25 docentes. El Instituto inicio sus operaciones con la carrera de Técnico Analista Programador el año 2000. A medida que pasaron los años se añadieron otras dos carreras. 

| **Carrera**  | **Nro. Estudiantes**  | **Año inicio**  | **Vacantes cada año**  |
| --- | ---: | ---: | ---: |
| Técnico Analista Programador  | 108  | 2000  | 50  |
| Técnico en Análisis de Datos  | 102  | 2018  | 50  |
| Técnico en Ciberseguridad  | 105  | 2012  | 50  |
| **Total**  | **315**  |   |   |

La gestión académica del Instituto involucra información relacionada con carreras, planes de estudio, asignaturas, estudiantes, docentes, oferta académica, inscripciones y calificaciones. 

Actualmente, parte de esta información se administra mediante planillas, documentos y sistemas no integrados, lo que dificulta mantener información académica consistente y controlar adecuadamente los diferentes procesos académicos. 

Para abordar esta problemática, el IPT ha decidido desarrollar **SIGA IPT (Sistema Integrado de Gestión Académica)**, una aplicación web destinada a centralizar y apoyar la gestión de sus principales procesos académicos. 

### 4.2 Principales actividades del modelo de negocio 

### 4.2.1 Gestión de estudiantes (Épica 1) 

SIGA IPT debe permitir registrar y administrar a los estudiantes pertenecientes al instituto. Para cada estudiante se deberá almacenar su RUN, nombre completo, correo electrónico, carrera y plan de estudios al que se encuentra asociado. 

Cada estudiante tendrá un estado académico que represente su situación actual dentro de la institución. Los estados considerados por el sistema son: 

- REGULAR: estudiante habilitado académicamente para continuar sus estudios. 
- POSTERGACIÓN: estudiante que ha postergado sus estudios. 
- RETIRO TEMPORAL: estudiante que ha interrumpido temporalmente sus estudios. 
- EGRESADO: estudiante que ha aprobado todas las asignaturas correspondientes a su plan de estudios. 
- ELIMINADO: estudiante que ha perdido su condición de estudiante de la carrera por haber reprobado una misma asignatura por segunda vez. 

El sistema deberá permitir consultar y actualizar la información de los estudiantes, así como acceder a su historial académico (asignaturas cursadas, calificaciones, etc.). 

**Restricciones y reglas de negocio** 

- Para registrar un estudiante se deberá proporcionar su RUN, nombre completo, correo electrónico y carrera. El estudiante quedará asociado al plan de estudios vigente correspondiente a dicha carrera. 
- El RUN y el correo electrónico de cada estudiante deben ser únicos dentro del sistema. 
- Todo estudiante deberá estar asociado a una carrera existente y a uno de los planes de estudio correspondientes a dicha carrera. 
- Al momento de su registro, todo estudiante deberá quedar inicialmente en estado REGULAR. 
- Un estudiante que posea inscripciones o historial académico no podrá ser eliminado físicamente del sistema. 
- Los estados EGRESADO y ELIMINADO no podrán ser asignados manualmente. Estos estados serán determinados automáticamente por SIGA IPT durante el proceso de cierre del período académico, de acuerdo con la situación académica del estudiante. 
- La carrera y el plan de estudios de un estudiante solo podrán modificarse mientras el estudiante no posea inscripciones ni historial académico. 
- Los estados REGULAR, POSTERGACIÓN y RETIRO TEMPORAL podrán ser asignados o modificados por el Administrador Académico, excepto el estado REGULAR inicial, que será asignado automáticamente al registrar al estudiante. 

### 4.2.2 Gestión de carreras y planes de estudio (Épica 2) 

SIGA IPT debe permitir definir y administrar las carreras ofrecidas por el instituto. Cada carrera contará con un código, nombre, descripción y estado, y tendrá una duración de dos años, correspondientes a cuatro semestres académicos. El estado de una carrera podrá ser ACTIVA o INACTIVA. 

La estructura curricular de cada carrera estará definida mediante un plan de estudios, en el cual se establecerán las asignaturas que los estudiantes deberán cursar durante su formación. Una carrera podrá contar con distintos planes de estudio a lo largo del tiempo. El **Apéndice A** muestra los actuales planes de estudio de cada carrera. 

Al momento de ingresar al instituto, cada estudiante deberá quedar asociado al plan de estudios vigente que corresponda a su carrera. 

**Restricciones y reglas de negocio** 

- El código de cada carrera deberá ser único dentro del sistema. 
- Toda carrera deberá contar con un código, nombre y descripción, y tendrá una duración de cuatro semestres académicos. 
- Una carrera podrá contar con distintos planes de estudio a lo largo del tiempo, pero solo uno de ellos podrá encontrarse vigente en un momento determinado. 
- Una carrera INACTIVA no podrá recibir nuevos estudiantes. 
- Todo plan de estudios deberá estar asociado a una carrera existente y contar con un identificador que permita distinguirlo de los demás planes de la misma carrera. 
- La creación de un nuevo plan de estudios no deberá modificar el plan asignado a los estudiantes que ingresaron bajo planes anteriores. 
- Un plan de estudios que se encuentre asociado a estudiantes no podrá ser eliminado físicamente del sistema. 
- Una carrera que tenga estudiantes o planes de estudio asociados no podrá ser eliminada físicamente; únicamente podrá modificarse su estado. 
- Al crear un nuevo plan de estudios y establecerlo como vigente, el plan que se encontraba vigente para la misma carrera deberá pasar automáticamente a estado NO VIGENTE. 

### 4.2.3 Gestión de asignaturas y prerrequisitos (Épica 3) 

SIGA IPT debe permitir definir y administrar las asignaturas que conforman los planes de estudio de las distintas carreras. Estas asignaturas se distribuirán entre los cuatro semestres correspondientes a la duración de cada carrera. El **Apéndice A** muestra las asignaturas de los actuales planes de estudio de cada carrera. 

Cada asignatura deberá contar con un código, nombre, semestre, TEL, SCT y sus prerrequisitos, cuando corresponda. El TEL representa las horas semanales de trabajo asociadas a la asignatura y se compone de horas de Teoría, Ejercicios y Laboratorio, mientras que el SCT corresponde a la cantidad de créditos académicos asignados a la asignatura. 

Dentro de un plan de estudios, una asignatura podrá establecer como prerrequisito una o más asignaturas del mismo plan. Esta información será utilizada posteriormente por el sistema para determinar si un estudiante cumple las condiciones necesarias para inscribir una determinada asignatura. 

**Restricciones y reglas de negocio** 

- Toda asignatura deberá contar con un código y nombre, y estar asociada a un plan de estudios existente. Su código deberá ser único dentro de dicho plan. 
- Toda asignatura deberá estar asociada a uno de los cuatro semestres del plan de estudios, utilizando valores entre 1 y 4. 
- El valor SCT de una asignatura deberá ser mayor que cero y menor o igual a 7. 
- Las horas semanales de Teoría, Ejercicios y Laboratorio no podrán tener valores negativos y la suma total de estas horas deberá ser inferior a 8 horas semanales. 
- Una asignatura podrá tener entre 0 y 3 prerrequisitos, los cuales deberán corresponder a asignaturas existentes dentro del mismo plan de estudios. 
- Los prerrequisitos de una asignatura deberán corresponder a asignaturas de semestres anteriores dentro del mismo plan de estudios. 
- Una asignatura que forme parte del historial académico de uno o más estudiantes no podrá ser eliminada físicamente del sistema. 
- Las modificaciones realizadas sobre una asignatura no deberán alterar la información registrada previamente en el historial académico de los estudiantes. 

### 4.2.4 Gestión de docentes (Épica 4) 

SIGA IPT debe permitir registrar y administrar a los docentes que participan en las actividades académicas del instituto. Para cada docente se deberá almacenar su RUN, nombre completo, correo electrónico, título profesional, grado académico y estado, el cual podrá ser ACTIVO o INACTIVO. 

Los docentes registrados podrán ser asignados como responsables de las secciones de asignaturas impartidas durante los distintos períodos académicos. 

**Restricciones y reglas de negocio** 

- El RUN y el correo electrónico de cada docente deberán ser únicos dentro del sistema. 
- Todo docente deberá contar con RUN, nombre completo, correo electrónico, título profesional y grado académico. 
- Al momento de su registro, todo docente deberá quedar inicialmente en estado ACTIVO. 
- Solo los docentes en estado ACTIVO podrán ser asignados como responsables de nuevas secciones. 
- Un docente que tenga secciones asociadas no podrá ser eliminado físicamente del sistema. 
- Un docente no podrá cambiar su estado a INACTIVO mientras tenga secciones asignadas en un período académico ABIERTO. 
- El cambio de estado de un docente no deberá modificar su información histórica ni las secciones que haya impartido anteriormente. 

### 4.2.5 Gestión de oferta académica y secciones (Épica 5) 

Antes del inicio de cada semestre, SIGA IPT debe permitir definir la oferta académica correspondiente al período académico. Cada período académico estará identificado por el año y el semestre en que se desarrollan las actividades académicas, utilizando una nomenclatura como 2027-1 o 2027-2. Cada período académico tendrá un estado, que podrá ser ABIERTO o CERRADO. 

La oferta académica estará compuesta por las asignaturas que serán impartidas durante el período. Para cada asignatura ofrecida se deberá crear una única sección, la cual tendrá asociado un docente responsable, un horario y una capacidad máxima de estudiantes. 

Los horarios de las secciones se organizarán en módulos distribuidos a lo largo de la jornada académica: 

| **Módulo**  | **Horario**  |
| --- | --- |
| M1  | 08:15 - 09:35  |
| M2  | 09:50 - 11:10  |
| M3  | 11:25 - 12:45  |
| M4  | 13:45 - 15:05  |
| M5  | 15:20 - 16:40  |
| M6  | 16:55 - 18:15  |

Cada bloque horario se identificará mediante un código que combina el día de la semana con el número del módulo. Para los días se utilizarán los códigos L (lunes), M (martes), W (miércoles), J 

(jueves) y V (viernes). De esta forma, por ejemplo, L2 representa el día lunes entre las 09:50 y las 11:10 horas. 

Una sección podrá tener asignados uno o más bloques horarios durante la semana. Por ejemplo, una sección con horario L2, W3 se impartirá los lunes en el módulo 2 y los miércoles en el módulo 

**Restricciones y reglas de negocio** 

- Al momento de su creación, todo período académico deberá quedar inicialmente en estado ABIERTO. 
- Toda sección deberá estar asociada a una asignatura existente y a un período académico. Una asignatura podrá contar con una única sección durante un mismo período académico. 
- Toda sección deberá tener asignado un docente responsable, quien deberá encontrarse en estado ACTIVO al momento de realizar la asignación. 
- La capacidad de una sección deberá ser mayor que cero y no podrá superar los 60 estudiantes. La cantidad de estudiantes inscritos no podrá superar la capacidad definida para la sección. 
- Toda sección deberá tener asignado al menos un bloque horario, utilizando un día válido (L, M, W, J o V) y un módulo entre 1 y 6. 
- La cantidad de bloques horarios asignados a una sección deberá corresponder a la cantidad total de horas TEL semanales de la asignatura, considerando que cada bloque corresponde a 2 horas pedagógicas. 
- Una sección no podrá tener bloques horarios duplicados. 
- Un docente no podrá tener asignadas dos secciones cuyos horarios coincidan en una misma combinación de día y módulo. El sistema deberá verificar la existencia de estos conflictos antes de realizar la asignación. 
- Una sección que tenga estudiantes inscritos no podrá ser eliminada físicamente del sistema. 

### 4.2.6 Inscripción académica (Épica 6) 

Al comienzo de cada período académico, SIGA IPT debe permitir que los estudiantes inscriban las asignaturas que cursarán durante el semestre. Para ello, el sistema mostrará las asignaturas disponibles de acuerdo con la carrera y el plan de estudios del estudiante, junto con la sección ofrecida para cada una de ellas. 

Para realizar la inscripción, el estudiante deberá seleccionar las asignaturas que desea cursar. Cada asignatura tendrá asociada la sección definida para el período académico correspondiente. 

El proceso administrativo de matrícula en el IPT se encuentra fuera del alcance de SIGA IPT. No obstante, el sistema dispondrá de la información necesaria para determinar si un estudiante se encuentra matriculado en un determinado período académico, ya que esta condición será considerada durante el proceso de inscripción. 

Antes de registrar las asignaturas seleccionadas, SIGA IPT deberá verificar automáticamente que el estudiante cumple las condiciones establecidas para su inscripción. 

**Restricciones y reglas de negocio** 

- Para realizar inscripciones académicas, el estudiante debe encontrarse en estado REGULAR y estar matriculado en el período académico correspondiente. 
- El estudiante solo podrá inscribir asignaturas pertenecientes a su plan de estudios que sean ofrecidas durante el período académico. 
- Las inscripciones y anulaciones solo podrán realizarse mientras el período académico se encuentre ABIERTO. 
- Una asignatura que ya haya sido aprobada no podrá ser inscrita nuevamente. Una asignatura reprobada una vez podrá volver a ser inscrita. 
- Para inscribir una asignatura, el estudiante deberá haber aprobado todos sus prerrequisitos, cuando corresponda. 
- Una asignatura no podrá ser inscrita más de una vez por el mismo estudiante durante un mismo período académico. 
- El estudiante deberá inscribir un mínimo de 3 y un máximo de 6 asignaturas por período académico. Si le quedan menos de tres asignaturas para completar su plan de estudios, podrá inscribir las asignaturas pendientes disponibles. 
- El estudiante no podrá inscribir asignaturas de un semestre superior mientras tenga asignaturas pendientes de semestres inferiores que se encuentren disponibles para inscripción. 
- Una asignatura podrá ser inscrita únicamente si su sección dispone de cupos. Una inscripción válida ocupará un cupo y su anulación lo dejará nuevamente disponible. 
- El estudiante no podrá inscribir asignaturas cuyas secciones presenten conflictos de horario entre sí. 
- Toda inscripción deberá quedar asociada al estudiante, asignatura, sección y período académico correspondiente, registrándose además la fecha en que fue realizada. 

### 4.2.7 Registro de calificaciones finales y cierre de semestre (Épica 7) 

Al finalizar cada período académico, SIGA IPT debe permitir que los docentes registren las calificaciones finales obtenidas por los estudiantes en las asignaturas cursadas. Cada docente será responsable de ingresar la calificación final de los estudiantes inscritos en las secciones que tiene asignadas. 

SIGA IPT no administrará las evaluaciones parciales realizadas durante el semestre ni las calificaciones obtenidas en ellas. El proceso mediante el cual se determina la calificación final de una asignatura se realizará fuera del sistema, por lo que únicamente se registrará la calificación final obtenida por cada estudiante. 

A partir de esta calificación, SIGA IPT determinará automáticamente el resultado de la asignatura. Un estudiante se considerará APROBADO cuando obtenga una calificación final igual o superior a 4,0; en caso contrario, se considerará REPROBADO. 

Una vez finalizado el registro de calificaciones, SIGA IPT deberá permitir realizar el cierre del período académico. Antes de efectuarlo, el sistema verificará que todas las inscripciones del período cuenten con una calificación final registrada. Si existen calificaciones pendientes, el cierre no podrá realizarse y el sistema deberá informar las secciones que presentan información incompleta. 

Cuando se cumplan las condiciones para realizar el cierre, las calificaciones y los resultados obtenidos pasarán a formar parte del historial académico de los estudiantes. Durante este proceso, SIGA IPT también actualizará su situación académica. Un estudiante que haya reprobado una misma asignatura por segunda vez quedará en estado ELIMINADO, mientras que aquel que haya aprobado todas las asignaturas correspondientes a su plan de estudios quedará en estado EGRESADO. 

Una vez completado el cierre, el período académico quedará cerrado y sus resultados no podrán ser modificados mediante las operaciones normales del sistema. 

**Restricciones y reglas de negocio** 

- Solo el docente responsable de una sección podrá registrar las calificaciones finales de los estudiantes inscritos en ella. 
- Solo podrán registrarse calificaciones para estudiantes que se encuentren inscritos en la sección correspondiente. 
- Cada inscripción podrá tener asociada como máximo una calificación final, la cual deberá encontrarse en el rango de 1,0 a 7,0. Las calificaciones podrán ser modificadas mientras el período académico se encuentre ABIERTO. 
- Una calificación final igual o superior a 4,0 determinará que la asignatura se encuentra APROBADA; una calificación inferior a 4,0 determinará que se encuentra REPROBADA. 
- Solo podrá ejecutarse el proceso de cierre sobre un período académico que se encuentre ABIERTO. 
- Para realizar el cierre, todas las inscripciones del período deberán contar con una calificación final. Si existen calificaciones pendientes, el cierre deberá ser rechazado y el sistema deberá informar las secciones que presentan información incompleta. 
- Durante el cierre, las calificaciones y resultados obtenidos deberán consolidarse en el historial académico de los estudiantes. 
- Si un estudiante reprueba una misma asignatura por segunda vez, su estado cambiará automáticamente a ELIMINADO, conservándose sus inscripciones, calificaciones e historial académico. 
- Si un estudiante ha aprobado todas las asignaturas correspondientes a su plan de estudios, su estado cambiará automáticamente a EGRESADO. 
- Una vez procesados los resultados y actualizada la situación académica de los estudiantes, el período cambiará de estado ABIERTO a CERRADO. 
- Un período académico CERRADO no permitirá registrar o modificar calificaciones, realizar nuevas inscripciones o anulaciones, ni ejecutar nuevamente el proceso normal de cierre. 

### 4.3 Actores del Sistema y permisos 

SIGA IPT considera tres tipos de usuarios, cada uno con acceso a las funcionalidades correspondientes a su rol: 

- **Administrador Académico:** responsable de la gestión académica del sistema. Puede administrar estudiantes, docentes, carreras, planes de estudio, asignaturas, períodos académicos, oferta académica y secciones. También puede consultar las inscripciones y realizar el cierre de los períodos académicos. 
- **Docente:** puede consultar las secciones que tiene asignadas y los estudiantes inscritos en ellas, así como registrar y modificar sus calificaciones finales mientras el período académico se encuentre abierto. 
- **Estudiante:** puede consultar su información académica, plan de estudios, oferta académica e historial académico. También puede realizar y consultar sus inscripciones durante los períodos habilitados para ello. 

## Apéndice A: Carreras Profesionales 

### 10450 - Técnico Analista Programador. Plan de estudios 2021.3 

| **Sem**  | **Código**  | **Asignatura**  | **TEL**  | **SCT**  | **Prerrequisitos**  |
| ---: | --- | --- | --- | ---: | --- |
| 1  | TAP101  | Fundamentos de Programación  | 2-2-2  | 6  | —  |
| 1  | TAP102  | Matemática Aplicada  | 2-2-0  | 5  | —  |
| 1  | TAP103  | Fundamentos de Computación  | 2-0-2  | 4  | —  |
| 1  | TAP104  | Bases de Datos I  | 2-0-2  | 5  | —  |
| 1  | TAP105  | Comunicación Efectiva  | 2-2-0  | 4  | —  |
| 1  | TAP106  | Inglés Técnico I  | 2-2-0  | 4  | —  |
| 2  | TAP201  | Programación Orientada a Objetos  | 2-2-2  | 6  | TAP101  |
| 2  | TAP202  | Estructuras de Datos  | 2-2-2  | 6  | TAP101  |
| 2  | TAP203  | Bases de Datos II  | 2-0-2  | 5  | TAP104  |
| 2  | TAP204  | Desarrollo Web I  | 2-0-4  | 6  | TAP101  |
| 2  | TAP205  | Sistemas Operativos  | 2-0-2  | 4  | TAP103  |
| 2  | TAP206  | Inglés Técnico II  | 2-2-0  | 3  | TAP106  |
| 3  | TAP301  | Desarrollo Web II  | 2-0-4  | 6  | TAP201, TAP204  |
| 3  | TAP302  | Ingeniería de Software  | 2-2-0  | 5  | TAP201  |
| 3  | TAP303  | Desarrollo de Aplicaciones Móviles  | 2-0-4  | 6  | TAP201  |
| 3  | TAP304  | Administración de Bases de Datos  | 2-0-2  | 5  | TAP203  |
| 3  | TAP305  | Redes y Comunicaciones  | 2-0-2  | 4  | TAP205  |
| 3  | TAP306  | Taller de Programación  | 0-0-4  | 4  | TAP201, TAP202  |
| 4  | TAP401  | Arquitectura y Desarrollo de Software  | 2-0-4  | 6  | TAP301, TAP302  |
| 4  | TAP402  | Desarrollo de Aplicaciones Cloud  | 2-0-4  | 6  | TAP301  |
| 4  | TAP403  | Seguridad de Aplicaciones  | 2-0-2  | 4  | TAP301, TAP305  |
| 4  | TAP404  | Calidad y Pruebas de Software  | 2-0-2  | 5  | TAP302  |
| 4  | TAP405  | Proyecto de Desarrollo de Software  | 0-0-6  | 6  | TAP301, TAP302, TAP306  |
| 4  | TAP406  | Emprendimiento y Gestión de Proyectos  | 2-2-0  | 3  | TAP302  |

### 10451 – Técnico en Análisis de Datos. Plan de estudios 2022.3 

| **Sem**  | **Código**  | **Asignatura**  | **TEL**  | **SCT**  | **Prerrequisitos**  |
| ---: | --- | --- | --- | ---: | --- |
| 1  | TAD101  | Fundamentos de Programación  | 2-2-2  | 6  | —  |
| 1  | TAD102  | Matemática Aplicada  | 2-2-0  | 5  | —  |
| 1  | TAD103  | Introducción al Análisis de Datos  | 2-0-2  | 5  | —  |
| 1  | TAD104  | Bases de Datos I  | 2-0-2  | 5  | —  |
| 1  | TAD105  | Herramientas Computacionales  | 0-0-4  | 4  | —  |
| 1  | TAD106  | Comunicación Efectiva  | 2-2-0  | 4  | —  |
| 2  | TAD201  | Programación para Análisis de Datos  | 2-0-4  | 6  | TAD101  |
| 2  | TAD202  | Probabilidad y Estadística  | 2-2-2  | 6  | TAD102  |
| 2  | TAD203  | Bases de Datos II  | 2-0-2  | 5  | TAD104  |
| 2  | TAD204  | Preparación y Calidad de Datos  | 2-0-4  | 5  | TAD103, TAD101  |
| 2  | TAD205  | Visualización de Datos  | 2-0-2  | 5  | TAD103  |
| 2  | TAD206  | Inglés Técnico  | 2-2-0  | 3  | —  |
| 3  | TAD301  | Estadística Aplicada  | 2-2-2  | 6  | TAD202  |
| 3  | TAD302  | Minería de Datos  | 2-0-4  | 6  | TAD201, TAD202  |
| 3  | TAD303  | Inteligencia de Negocios  | 2-0-4  | 5  | TAD203, TAD205  |
| 3  | TAD304  | Integración y Procesamiento de Datos  | 2-0-4  | 5  | TAD201, TAD203  |

| 3  | TAD305  | Visualización Avanzada de Datos  | 2-0-2  | 4  | TAD205  |
| ---: | --- | --- | --- | ---: | --- |
| 3  | TAD306  | Ética y Protección de Datos  | 2-2-0  | 4  | TAD103  |
| 4  | TAD401  | Fundamentos de Machine Learning  | 2-0-4  | 6  | TAD301, TAD302  |
| 4  | TAD402  | Big Data y Procesamiento de Datos  | 2-0-4  | 5  | TAD304  |
| 4  | TAD403  | Analítica de Negocios  | 2-0-2  | 5  | TAD301, TAD303  |
| 4  | TAD404  | Desarrollo de Soluciones de Datos  | 2-0-4  | 5  | TAD302, TAD304  |
| 4  | TAD405  | Proyecto de Análisis de Datos  | 0-0-6  | 6  | TAD302, TAD303, TAD305  |
| 4  | TAD406  | Emprendimiento y Gestión de Proyectos  | 2-2-0  | 3  | —  |

### 10452 – Técnico en Ciberseguridad. Plan de estudios 2021.3 

| **Sem**  | **Código**  | **Asignatura**  | **TEL**  | **SCT**  | **Prerrequisitos**  |
| ---: | --- | --- | --- | ---: | --- |
| 1  | TCS101  | Fundamentos de Programación  | 2-2-2  | 6  | —  |
| 1  | TCS102  | Fundamentos de Sistemas Computacionales  | 2-0-2  | 5  | —  |
| 1  | TCS103  | Redes de Computadores I  | 2-0-2  | 5  | —  |
| 1  | TCS104  | Sistemas Operativos  | 2-0-2  | 5  | —  |
| 1  | TCS105  | Matemática Aplicada  | 2-2-0  | 5  | —  |
| 1  | TCS106  | Comunicación Efectiva  | 2-2-0  | 4  | —  |
| 2  | TCS201  | Programación para Ciberseguridad  | 2-0-4  | 5  | TCS101  |
| 2  | TCS202  | Redes de Computadores II  | 2-0-4  | 6  | TCS103  |
| 2  | TCS203  | Administración de Sistemas Linux  | 2-0-4  | 5  | TCS104  |
| 2  | TCS204  | Fundamentos de Ciberseguridad  | 2-2-2  | 6  | TCS102, TCS103  |
| 2  | TCS205  | Bases de Datos  | 2-0-2  | 5  | TCS101  |
| 2  | TCS206  | Inglés Técnico  | 2-2-0  | 3  | —  |
| 3  | TCS301  | Seguridad de Redes  | 2-0-4  | 6  | TCS202, TCS204  |
| 3  | TCS302  | Seguridad de Sistemas Operativos  | 2-0-4  | 5  | TCS203, TCS204  |
| 3  | TCS303  | Criptografía Aplicada  | 2-2-2  | 5  | TCS204  |
| 3  | TCS304  | Seguridad de Aplicaciones Web  | 2-0-4  | 5  | TCS201, TCS204  |
| 3  | TCS305  | Monitoreo y Detección de Amenazas  | 2-0-4  | 5  | TCS202, TCS204  |
| 3  | TCS306  | Legislación y Ética en Ciberseguridad  | 2-2-0  | 4  | TCS204  |
| 4  | TCS401  | Ethical Hacking y Pruebas de Penetración  | 2-0-4  | 6  | TCS301, TCS302  |
| 4  | TCS402  | Gestión de Incidentes de Ciberseguridad  | 2-0-4  | 5  | TCS305  |
| 4  | TCS403  | Seguridad en Cloud  | 2-0-4  | 5  | TCS301, TCS302  |
| 4  | TCS404  | Análisis Forense Digital  | 2-0-4  | 5  | TCS302  |
| 4  | TCS405  | Proyecto de Ciberseguridad  | 0-0-6  | 6  | TCS301, TCS302, TCS305  |
| 4  | TCS406  | Gestión de Riesgos de Ciberseguridad  | 2-2-0  | 3  | TCS204  |
