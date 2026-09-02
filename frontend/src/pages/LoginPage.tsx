import { useState, type FormEvent } from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { errorMessage } from '../lib/api';
import { useAuth } from '../lib/auth';
import { ErrorBox } from '../components/ui';

export function LoginPage() {
  const { login } = useAuth();
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
        bgcolor: 'primary.main',
        p: 2,
      }}
    >
      <Paper sx={{ p: 4, width: '100%', maxWidth: 380 }} elevation={6}>
        <Box sx={{ textAlign: 'center', mb: 2 }}>
          <Box
            component="img"
            src="/logo.png"
            alt="Sasi Dairy Farm"
            onError={(e) => {
              (e.currentTarget as HTMLImageElement).style.display = 'none';
              (e.currentTarget.nextElementSibling as HTMLElement | null)?.style.setProperty('display', 'block');
            }}
            sx={{ maxWidth: 180, maxHeight: 140, objectFit: 'contain' }}
          />
          <Typography variant="h5" color="primary" sx={{ display: 'none' }}>
            SASI MILK SERVICE
          </Typography>
        </Box>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3, textAlign: 'center' }}>
          Sign in to continue
        </Typography>
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
