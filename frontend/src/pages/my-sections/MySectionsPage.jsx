import { useMemo, useState } from 'react';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import StatusChip from '../../components/domain/StatusChip';
import SelectField from '../../components/form/SelectField';
import EmptyState from '../../components/ui/EmptyState';
import ErrorAlert from '../../components/ui/ErrorAlert';
import Loader from '../../components/ui/Loader';
import PageHeader from '../../components/ui/PageHeader';
import { useAuth } from '../../hooks/useAuth';
import { useTeacherSections } from '../../hooks/useTeacherSections';
import { PERIOD_STATUS } from '../../utils/enums';
import SectionAccordion from './SectionAccordion';

const ALL_PERIODS = '';

/**
 * Secciones a cargo del docente autenticado, con sus estudiantes inscritos.
 *
 * El filtro por periodo se aplica en el cliente porque GET /api/sections acepta
 * un filtro a la vez: o el periodo o el docente.
 */
export default function MySectionsPage() {
    const { email } = useAuth();
    const { teacher, teacherMissing, sections, periods, loading, error, reload } = useTeacherSections();

    const [periodFilter, setPeriodFilter] = useState(ALL_PERIODS);

    const periodOptions = useMemo(
        () =>
            [...periods]
                .sort((a, b) => b.code.localeCompare(a.code))
                .map((period) => ({
                    value: String(period.id),
                    label: `${period.code} (${PERIOD_STATUS[period.status]?.label ?? period.status})`,
                })),
        [periods],
    );

    const visibleSections = useMemo(
        () =>
            sections
                .filter(
                    (section) =>
                        periodFilter === ALL_PERIODS || String(section.academicPeriodId) === periodFilter,
                )
                .sort(
                    (a, b) =>
                        b.academicPeriodCode.localeCompare(a.academicPeriodCode) ||
                        a.subjectCode.localeCompare(b.subjectCode),
                ),
        [sections, periodFilter],
    );

    return (
        <>
            <PageHeader
                title="Mis secciones"
                subtitle={
                    teacher ? `${teacher.fullName} · ${teacher.email}` : 'Secciones donde eres el docente responsable'
                }
                action={teacher && <StatusChip kind="teacher" value={teacher.status} />}
            />

            <ErrorAlert error={error} onRetry={reload} />

            {teacherMissing ? (
                <Paper variant="outlined">
                    <EmptyState
                        title="Tu usuario no esta registrado como docente"
                        description={`Ningun docente del sistema tiene el email ${email}. Un administrador debe registrarte para que puedas tener secciones y calificar.`}
                    />
                </Paper>
            ) : (
                <>
                    <Box sx={{ maxWidth: 320, mb: 2 }}>
                        <SelectField
                            label="Periodo academico"
                            value={periodFilter}
                            onChange={(event) => setPeriodFilter(event.target.value)}
                            options={periodOptions}
                            placeholder="Todos los periodos"
                        />
                    </Box>

                    {loading ? (
                        <Loader label="Cargando tus secciones..." />
                    ) : visibleSections.length === 0 ? (
                        <Paper variant="outlined">
                            <EmptyState
                                title="Sin secciones"
                                description={
                                    periodFilter === ALL_PERIODS
                                        ? 'Todavia no tienes secciones asignadas. Las asigna un administrador al armar la oferta academica.'
                                        : 'No tienes secciones en el periodo seleccionado.'
                                }
                            />
                        </Paper>
                    ) : (
                        visibleSections.map((section) => <SectionAccordion key={section.id} section={section} />)
                    )}
                </>
            )}
        </>
    );
}
