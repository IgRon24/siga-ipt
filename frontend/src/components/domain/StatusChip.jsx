import Chip from '@mui/material/Chip';
import { STATUS_CATALOGS } from '../../utils/enums';

/**
 * Estado de un enum del backend con su etiqueta y color.
 *
 *   <StatusChip kind="student" value="REGULAR" />
 *   kind: career | studyPlan | student | teacher | period | enrollment | matriculation | result
 */
export default function StatusChip({ kind, value, size = 'small' }) {
    if (!value) return null;
    const meta = STATUS_CATALOGS[kind]?.[value];
    return <Chip label={meta?.label ?? value} color={meta?.color ?? 'default'} size={size} variant="outlined" />;
}
