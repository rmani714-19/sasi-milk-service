import { useState, type FormEvent } from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { errorMessage } from '../lib/api';
import { useAuth } from '../lib/auth';
import { useAppTheme } from '../lib/appTheme';
import { MODE_GRADIENTS } from '../theme';
import { ErrorBox } from '../components/ui';

export function LoginPage() {
  const { login } = useAuth();
  const { mode } = useAppTheme();
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(username.trim(), password);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'grid',
        placeItems: 'center',
        background: MODE_GRADIENTS[mode],
        backgroundAttachment: 'fixed',
        p: 2,
      }}
    >
      <Paper sx={{ p: { xs: 3, sm: 4 }, width: '100%', maxWidth: 400, borderRadius: 3 }} elevation={0}>
        <Box sx={{ textAlign: 'center', mb: 3 }}>
          <Box
            component="img"
            src="/logo.png"
            alt="Sasi Dairy Farm"
            onError={(e) => ((e.currentTarget as HTMLImageElement).style.display = 'none')}
            sx={{ width: '100%', maxWidth: 170, height: 'auto', display: 'block', mx: 'auto', mb: 1 }}
          />
          <Typography variant="h6" sx={{ fontWeight: 700, color: 'primary.dark' }}>
            SASI MILK SERVICE
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
            Sign in to continue
          </Typography>
        </Box>
        <form onSubmit={submit}>
          <Stack spacing={2}>
            <TextField
              label="Username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              autoFocus
              autoComplete="username"
            />
            <TextField
              label="Password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
            />
            {error ? <ErrorBox error={error} /> : null}
            <Button type="submit" variant="contained" size="large" disabled={busy || !password}>
              {busy ? 'Signing in…' : 'Sign in'}
            </Button>
          </Stack>
        </form>
      </Paper>
    </Box>
  );
}
