import { useEffect, useState } from 'react';
import { Link as RouterLink, Outlet, useLocation } from 'react-router-dom';
import AppBar from '@mui/material/AppBar';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import Drawer from '@mui/material/Drawer';
import IconButton from '@mui/material/IconButton';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Toolbar from '@mui/material/Toolbar';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import MenuIcon from '@mui/icons-material/Menu';
import LogoutIcon from '@mui/icons-material/Logout';
import CloudOffIcon from '@mui/icons-material/CloudOff';
import CloudDoneIcon from '@mui/icons-material/CloudDone';
import WbSunnyIcon from '@mui/icons-material/WbSunny';
import DarkModeIcon from '@mui/icons-material/DarkMode';
import DashboardIcon from '@mui/icons-material/SpaceDashboard';
import LocalDrinkIcon from '@mui/icons-material/LocalDrink';
import AgricultureIcon from '@mui/icons-material/Agriculture';
import ReceiptLongIcon from '@mui/icons-material/ReceiptLong';
import PriceChangeIcon from '@mui/icons-material/PriceChange';
import RedeemIcon from '@mui/icons-material/Redeem';
import RemoveCircleIcon from '@mui/icons-material/RemoveCircleOutline';
import SavingsIcon from '@mui/icons-material/Savings';
import AssessmentIcon from '@mui/icons-material/Assessment';
import LocalShippingIcon from '@mui/icons-material/LocalShipping';
import NotificationsIcon from '@mui/icons-material/NotificationsActive';
import StoreIcon from '@mui/icons-material/Store';
import { useAuth } from '../lib/auth';
import { useAppTheme } from '../lib/appTheme';
import { usePendingSync } from '../lib/usePendingSync';

const DRAWER_WIDTH = 236;

interface NavItem {
  to: string;
  label: string;
  icon: JSX.Element;
  perm?: string;
}

const NAV: NavItem[] = [
  { to: '/', label: 'Dashboard', icon: <DashboardIcon />, perm: 'VIEW_DASHBOARD' },
  { to: '/collection', label: 'Milk Collection', icon: <LocalDrinkIcon />, perm: 'CREATE_COLLECTION' },
  { to: '/producers', label: 'Farmers', icon: <AgricultureIcon />, perm: 'VIEW_PRODUCERS' },
  { to: '/rates', label: 'Milk Rate', icon: <PriceChangeIcon />, perm: 'MANAGE_RATES' },
  { to: '/incentives', label: 'Incentives', icon: <RedeemIcon />, perm: 'MANAGE_INCENTIVES' },
  { to: '/deductions', label: 'Deductions', icon: <RemoveCircleIcon />, perm: 'MANAGE_DEDUCTIONS' },
  { to: '/advances', label: 'Advances', icon: <SavingsIcon />, perm: 'MANAGE_ADVANCES' },
  { to: '/settlements', label: 'Payments', icon: <ReceiptLongIcon />, perm: 'GENERATE_SETTLEMENT' },
  { to: '/reports', label: 'Reports', icon: <AssessmentIcon />, perm: 'VIEW_REPORTS' },
  { to: '/vehicles', label: 'Vehicles', icon: <LocalShippingIcon />, perm: 'VIEW_DASHBOARD' },
  { to: '/notifications', label: 'Notifications', icon: <NotificationsIcon />, perm: 'ALL' },
  { to: '/center', label: 'Centre', icon: <StoreIcon />, perm: 'MANAGE_RATES' },
];

export function AppLayout() {
  const { user, logout, can } = useAuth();
  const { mode, auto, toggle } = useAppTheme();
  const [mobileOpen, setMobileOpen] = useState(false);
  const location = useLocation();
  const { pending, online, syncing, sync } = usePendingSync();

  useEffect(() => setMobileOpen(false), [location.pathname]);

  const items = NAV.filter((n) => !n.perm || can(n.perm));

  const drawer = (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <Box sx={{ px: 2, pt: 2, pb: 1.5, display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
        <Box
          component="img"
          src="/logo.png"
          alt="Sasi Dairy Farm"
          onError={(e) => {
            (e.currentTarget as HTMLImageElement).style.display = 'none';
            (e.currentTarget.nextElementSibling as HTMLElement | null)?.style.setProperty('display', 'block');
          }}
          sx={{ width: '100%', maxWidth: 148, height: 'auto', display: 'block' }}
        />
        <Typography variant="h6" color="primary" align="center" sx={{ display: 'none', lineHeight: 1.2 }}>
          SASI MILK
          <br />
          SERVICE
        </Typography>
      </Box>
      <Divider />
      <List sx={{ flex: 1, py: 1 }}>
        {items.map((n) => (
          <ListItemButton
            key={n.to}
            component={RouterLink}
            to={n.to}
            selected={n.to === '/' ? location.pathname === '/' : location.pathname.startsWith(n.to)}
            sx={{ borderRadius: 2, mx: 1, mb: 0.5 }}
          >
            <ListItemIcon sx={{ minWidth: 40 }}>{n.icon}</ListItemIcon>
            <ListItemText primary={n.label} />
          </ListItemButton>
        ))}
      </List>
      <Divider />
      <Box sx={{ p: 2 }}>
        <Typography variant="body2" fontWeight={600}>
          {user?.fullName}
        </Typography>
        <Typography variant="caption" color="text.secondary">
          {user?.username}
        </Typography>
      </Box>
    </Box>
  );

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      <AppBar
        position="fixed"
        color="inherit"
        elevation={0}
        sx={{ width: { md: `calc(100% - ${DRAWER_WIDTH}px)` }, ml: { md: `${DRAWER_WIDTH}px` }, borderBottom: 1, borderColor: 'divider' }}
      >
        <Toolbar>
          <IconButton edge="start" onClick={() => setMobileOpen(true)} sx={{ mr: 1, display: { md: 'none' } }}>
            <MenuIcon />
          </IconButton>
          <Typography variant="h6" sx={{ flex: 1 }} noWrap>
            {items.find((n) => (n.to === '/' ? location.pathname === '/' : location.pathname.startsWith(n.to)))?.label ?? ''}
          </Typography>

          {pending > 0 && (
            <Tooltip title={online ? 'Tap to sync now' : 'Offline — will sync when back online'}>
              <Chip
                size="small"
                color={online ? 'warning' : 'default'}
                icon={online ? <CloudDoneIcon /> : <CloudOffIcon />}
                label={syncing ? 'Syncing…' : `${pending} pending`}
                onClick={() => online && sync()}
                sx={{ mr: 1 }}
              />
            </Tooltip>
          )}
          {pending === 0 && !online && (
            <Chip size="small" icon={<CloudOffIcon />} label="Offline" sx={{ mr: 1 }} />
          )}

          <Tooltip title={`${mode === 'morning' ? 'Morning' : 'Evening'} theme${auto ? ' (auto)' : ''} — tap to switch`}>
            <IconButton color="inherit" onClick={toggle}>
              {mode === 'morning' ? <WbSunnyIcon /> : <DarkModeIcon />}
            </IconButton>
          </Tooltip>
          <Tooltip title="Sign out">
            <IconButton color="inherit" onClick={logout}>
              <LogoutIcon />
            </IconButton>
          </Tooltip>
        </Toolbar>
      </AppBar>

      <Box component="nav" sx={{ width: { md: DRAWER_WIDTH }, flexShrink: { md: 0 } }}>
        <Drawer
          variant="temporary"
          open={mobileOpen}
          onClose={() => setMobileOpen(false)}
          ModalProps={{ keepMounted: true }}
          sx={{ display: { xs: 'block', md: 'none' }, '& .MuiDrawer-paper': { width: DRAWER_WIDTH } }}
        >
          {drawer}
        </Drawer>
        <Drawer
          variant="permanent"
          open
          sx={{ display: { xs: 'none', md: 'block' }, '& .MuiDrawer-paper': { width: DRAWER_WIDTH, borderRight: 1, borderColor: 'divider' } }}
        >
          {drawer}
        </Drawer>
      </Box>

      <Box component="main" sx={{ flexGrow: 1, width: { md: `calc(100% - ${DRAWER_WIDTH}px)` }, p: { xs: 2, sm: 3 } }}>
        <Toolbar />
        <Outlet />
      </Box>
    </Box>
  );
}
