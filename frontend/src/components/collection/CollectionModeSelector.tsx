import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import ScaleOutlinedIcon from '@mui/icons-material/ScaleOutlined';
import KeyboardOutlinedIcon from '@mui/icons-material/KeyboardOutlined';

export type CollectionMode = 'AUTO' | 'MANUAL';

export function CollectionModeSelector({
  value,
  onChange,
}: {
  value: CollectionMode;
  onChange: (m: CollectionMode) => void;
}) {
  return (
    <ToggleButtonGroup
      exclusive
      color="primary"
      value={value}
      onChange={(_, v: CollectionMode | null) => v && onChange(v)}
      sx={{ '& .MuiToggleButton-root': { px: 2, py: 1, fontWeight: 600 } }}
    >
      <ToggleButton value="AUTO">
        <ScaleOutlinedIcon sx={{ mr: 1, fontSize: 20 }} />
        Automatic Scale
      </ToggleButton>
      <ToggleButton value="MANUAL">
        <KeyboardOutlinedIcon sx={{ mr: 1, fontSize: 20 }} />
        Manual Entry
      </ToggleButton>
    </ToggleButtonGroup>
  );
}
