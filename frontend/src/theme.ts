import { createTheme } from '@mui/material/styles';

export const theme = createTheme({
  palette: {
    primary: { main: '#00695c' },
    secondary: { main: '#f9a825' },
    background: { default: '#f4f6f8' },
  },
  shape: { borderRadius: 10 },
  typography: {
    h5: { fontWeight: 700 },
    h6: { fontWeight: 700 },
    button: { textTransform: 'none', fontWeight: 600 },
  },
  components: {
    MuiButton: { defaultProps: { disableElevation: true } },
    MuiTextField: { defaultProps: { size: 'small', fullWidth: true } },
    MuiPaper: { styleOverrides: { root: { backgroundImage: 'none' } } },
  },
});
