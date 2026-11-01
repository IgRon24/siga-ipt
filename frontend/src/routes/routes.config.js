import AssignmentTurnedInIcon from '@mui/icons-material/AssignmentTurnedIn';
import CalendarMonthIcon from '@mui/icons-material/CalendarMonth';
import ClassIcon from '@mui/icons-material/Class';
import DashboardIcon from '@mui/icons-material/Dashboard';
import GradingIcon from '@mui/icons-material/Grading';
import HistoryIcon from '@mui/icons-material/History';
import LibraryBooksIcon from '@mui/icons-material/LibraryBooks';
import MenuBookIcon from '@mui/icons-material/MenuBook';
import PeopleIcon from '@mui/icons-material/People';
import SchoolIcon from '@mui/icons-material/School';
import ScheduleIcon from '@mui/icons-material/Schedule';
import TaskAltIcon from '@mui/icons-material/TaskAlt';
import BadgeIcon from '@mui/icons-material/Badge';

import DashboardPage from '../pages/dashboard/DashboardPage';
import CareersPage from '../pages/careers/CareersPage';
import PeriodsPage from '../pages/periods/PeriodsPage';
import SectionsPage from '../pages/sections/SectionsPage';
import StudentsPage from '../pages/students/StudentsPage';
import StudyPlansPage from '../pages/study-plans/StudyPlansPage';
import SubjectsPage from '../pages/subjects/SubjectsPage';
import TeachersPage from '../pages/teachers/TeachersPage';
import ClosurePage from '../pages/closure/ClosurePage';
import EnrollmentPage from '../pages/enrollments/EnrollmentPage';
import GradeSheetPage from '../pages/grades/GradeSheetPage';
import MyHistoryPage from '../pages/my-history/MyHistoryPage';
import MySectionsPage from '../pages/my-sections/MySectionsPage';

export const ROLES = { ADMIN: 'ADMIN', TEACHER: 'TEACHER', STUDENT: 'STUDENT' };
const { ADMIN, TEACHER, STUDENT } = ROLES;
export const ALL_ROLES = [ADMIN, TEACHER, STUDENT];

/**
 * Fuente unica del ruteo: de aqui salen las rutas, el guard por rol, el menu
 * lateral y los accesos del dashboard. Es el espejo de los @PreAuthorize del
 * backend, para que la interfaz no ofrezca lo que la API va a rechazar.
 *
 * Component se guarda como referencia (este archivo es .js, sin JSX) y props
 * son los datos fijos de la pantalla, hoy los del marcador pendiente.
 */
export const appRoutes = [
    {
        path: '/dashboard',
        label: 'Inicio',
        icon: DashboardIcon,
        roles: ALL_ROLES,
        Component: DashboardPage,
    },

    // --- ADMIN: datos academicos base ---
    {
        path: '/careers',
        label: 'Carreras',
        icon: SchoolIcon,
        roles: [ADMIN],
        Component: CareersPage,
        description: 'Codigo unico y un plan vigente por carrera',
    },
    {
        path: '/study-plans',
        label: 'Planes de estudio',
        icon: MenuBookIcon,
        roles: [ADMIN],
        Component: StudyPlansPage,
        description: 'Un solo plan VIGENTE por carrera',
    },
    {
        path: '/subjects',
        label: 'Asignaturas',
        icon: LibraryBooksIcon,
        roles: [ADMIN],
        Component: SubjectsPage,
        description: 'Horas TEL, SCT y prerrequisitos',
    },
    {
        path: '/teachers',
        label: 'Docentes',
        icon: BadgeIcon,
        roles: [ADMIN],
        Component: TeachersPage,
        description: 'Solo un docente ACTIVO puede tomar secciones',
    },
    {
        path: '/students',
        label: 'Estudiantes',
        icon: PeopleIcon,
        roles: [ADMIN],
        Component: StudentsPage,
        description: 'Registro, estado y matricula por periodo',
    },

    // --- ADMIN: oferta academica ---
    {
        path: '/periods',
        label: 'Periodos',
        icon: CalendarMonthIcon,
        roles: [ADMIN],
        Component: PeriodsPage,
        description: 'Periodos ABIERTO y matricula',
    },
    {
        path: '/sections',
        label: 'Secciones',
        icon: ScheduleIcon,
        roles: [ADMIN],
        Component: SectionsPage,
        description: 'Una seccion por asignatura y periodo',
    },
    {
        path: '/closure',
        label: 'Cierre de periodo',
        icon: TaskAltIcon,
        roles: [ADMIN],
        Component: ClosurePage,
        description: 'Consolida el historial y cierra el periodo',
    },

    // --- TEACHER ---
    {
        path: '/my-sections',
        label: 'Mis secciones',
        icon: ClassIcon,
        roles: [TEACHER],
        Component: MySectionsPage,
        description: 'Secciones a cargo y estudiantes inscritos',
    },
    {
        path: '/grades',
        label: 'Calificaciones',
        icon: GradingIcon,
        roles: [TEACHER],
        Component: GradeSheetPage,
        description: 'Acta por seccion, nota 1.0 a 7.0',
    },

    // --- STUDENT ---
    {
        path: '/enrollments',
        label: 'Inscripcion',
        icon: AssignmentTurnedInIcon,
        roles: [STUDENT],
        Component: EnrollmentPage,
        description: 'Entre 3 y 6 asignaturas por periodo',
    },
    {
        path: '/my-history',
        label: 'Mi historial',
        icon: HistoryIcon,
        roles: [STUDENT],
        Component: MyHistoryPage,
        description: 'Asignaturas aprobadas y reprobadas',
    },
];

/** Las rutas que puede ver un conjunto de roles, para el menu y el dashboard. */
export function routesForRoles(roles = []) {
    return appRoutes.filter((route) => route.roles.some((role) => roles.includes(role)));
}
