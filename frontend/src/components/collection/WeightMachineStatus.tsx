import Chip from '@mui/material/Chip';
import Tooltip from '@mui/material/Tooltip';
import CircularProgress from '@mui/material/CircularProgress';
import ScaleOutlinedIcon from '@mui/icons-material/ScaleOutlined';
import SensorsOffOutlinedIcon from '@mui/icons-material/SensorsOffOutlined';
import type { BridgeStatus } from '../../lib/weightBridge';

const META: Record<BridgeStatus, { label: string; color: 'success' | 'error' | 'warning' | 'default'; tip: string }> = {
  connected: { label: 'Scale connected', color: 'success', tip: 'Live weight is being received from the scale.' },
  connecting: { label: 'Connecting…', color: 'warning', tip: 'Connecting to the weight bridge…' },
  'no-scale': {
    label: 'Scale not detected',
    color: 'warning',
    tip: 'The weight bridge is running but no weight is coming from the scale. Check the RS-232 cable / power, or switch to Manual Entry.',
  },
  unavailable: {
    label: 'Weight bridge unavailable',
    color: 'error',
    tip: 'The local weight bridge service is not reachable. Start it on this PC, or switch to Manual Entry.',
  },
};

export function WeightMachineStatus({ status, onRetry }: { status: BridgeStatus; onRetry?: () => void }) {
  const m = META[status];
  const icon =
    status === 'connecting' ? (
      <CircularProgress size={14} thickness={6} />
    ) : status === 'connected' ? (
      <ScaleOutlinedIcon />
    ) : (
      <SensorsOffOutlinedIcon />
    );
  return (
    <Tooltip title={m.tip}>
      <Chip
        size="small"
        icon={icon}
        label={m.label}
        color={m.color}
        variant={status === 'connected' ? 'filled' : 'outlined'}
        onClick={status === 'connected' ? undefined : onRetry}
        sx={{ fontWeight: 600 }}
      />
    </Tooltip>
  );
}
