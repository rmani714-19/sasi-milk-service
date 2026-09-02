import type { ReactNode } from 'react';
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';

/** A titled group of form fields — consistent spacing across every dialog/form. */
export function FormSection({
  title,
  hint,
  children,
}: {
  title: string;
  hint?: ReactNode;
  children: ReactNode;
}) {
  return (
    <Box>
      <Typography
        variant="overline"
        sx={{ color: 'text.secondary', display: 'block', lineHeight: 1.6 }}
      >
        {title}
      </Typography>
      {hint && (
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
          {hint}
        </Typography>
      )}
      <Stack spacing={2} sx={{ mt: hint ? 0 : 1 }}>
        {children}
      </Stack>
    </Box>
  );
}
