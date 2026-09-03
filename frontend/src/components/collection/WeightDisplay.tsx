import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import SyncOutlinedIcon from '@mui/icons-material/SyncOutlined';
import HourglassEmptyOutlinedIcon from '@mui/icons-material/HourglassEmptyOutlined';
import SensorsOffOutlinedIcon from '@mui/icons-material/SensorsOffOutlined';
import type { BridgeStatus } from '../../lib/weightBridge';

/** Live machine weight — the biggest thing on the screen in Automatic mode. */
export function WeightDisplay({
  status,
  weightKg,
  unit,
  stable,
  stableHeld,
  armed,
}: {
  status: BridgeStatus;
  weightKg: number | null;
  unit: string;
  stable: boolean;
  stableHeld: boolean;
  /** false right after a save until the pan returns toward zero */
  armed: boolean;
}) {
  const connected = status === 'connected';
  const showKg = connected && weightKg != null ? weightKg.toFixed(3) : '––.–––';

  let sub: { text: string; icon: JSX.Element; color: string };
  if (!connected) {
    sub = { text: 'Scale not connected', icon: <SensorsOffOutlinedIcon />, color: 'error.main' };
  } else if (!armed) {
    sub = { text: 'Remove the can — waiting for scale to return to zero', icon: <HourglassEmptyOutlinedIcon />, color: 'text.secondary' };
  } else if (weightKg != null && weightKg < 0.05) {
    sub = { text: 'Waiting for milk', icon: <HourglassEmptyOutlinedIcon />, color: 'text.secondary' };
  } else if (stableHeld) {
    sub = { text: 'Stable', icon: <CheckCircleIcon />, color: 'success.main' };
  } else if (stable) {
    sub = { text: 'Settling…', icon: <SyncOutlinedIcon />, color: 'warning.main' };
  } else {
    sub = { text: 'Weight changing', icon: <SyncOutlinedIcon />, color: 'warning.main' };
  }

  const good = connected && armed && stableHeld && (weightKg ?? 0) >= 0.05;

  return (
    <Box
      sx={{
        textAlign: 'center',
        py: { xs: 2, sm: 2.5 },
        px: 2,
        borderRadius: 3,
        border: 2,
        borderColor: good ? 'success.main' : connected ? 'divider' : 'error.light',
        bgcolor: good ? 'rgba(46,125,50,0.06)' : 'action.hover',
        transition: 'border-color .2s, background-color .2s',
      }}
    >
      <Typography variant="overline" color="text.secondary" sx={{ letterSpacing: 1 }}>
        Live scale weight
      </Typography>
      <Typography
        sx={{
          fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
          fontSize: { xs: 44, sm: 60 },
          fontWeight: 800,
          lineHeight: 1.05,
          color: good ? 'success.dark' : 'text.primary',
        }}
      >
        {showKg}
        <Typography component="span" sx={{ fontSize: { xs: 20, sm: 26 }, fontWeight: 700, ml: 1 }} color="text.secondary">
          {unit.toUpperCase()}
        </Typography>
      </Typography>
      <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="center" sx={{ mt: 0.5, color: sub.color }}>
        <Box sx={{ display: 'flex', '& svg': { fontSize: 18 } }}>{sub.icon}</Box>
        <Typography variant="body2" sx={{ fontWeight: 600 }}>
          {sub.text}
        </Typography>
      </Stack>
    </Box>
  );
}
