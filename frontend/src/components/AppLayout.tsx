import { useEffect, useMemo, useState } from 'react';
import { Link as RouterLink, Outlet, useLocation } from 'react-router-dom';
import AppBar from '@mui/material/AppBar';
import Avatar from '@mui/material/Avatar';
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
import useMediaQuery from '@mui/material/useMediaQuery';
import { useTheme } from '@mui/material/styles';
import MenuIcon from '@mui/icons-material/Menu';
import MenuOpenIcon from '@mui/icons-material/MenuOpen';
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

const NAV_FULL = 240;
const NAV_MINI = 72;
const COLLAPSE_KEY = 'sasi.navCollapsed';

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

function readCollapsed(): boolean | null {
  try {
    const v = localStorage.getItem(COLLAPSE_KEY);
    return v == null ? null : v === '1';
  } catch {
    return null;
  }
}

export function AppLayout() {
  const { user, logout, can } = useAuth();
  const { mode, auto, toggle } = useAppTheme();
  const theme = useTheme();
  const location = useLocation();
  const { pending, online, syncing, sync } = usePendingSync();

  const isMobile = useMediaQuery(theme.breakpoints.down('sm')); // < 600px → overlay drawer
  const isLarge = useMediaQuery(theme.breakpoints.up('lg')); // ≥ 1200px → expanded by default

  const [mobileOpen, setMobileOpen] = useState(false);
  const [collapsed, setCollapsed] = useState<boolean>(() => readCollapsed() ?? true);

  // first run with no saved preference: expand on large screens, rail on tablet
  useEffect(() => {
    if (readCollapsed() == null) setCollapsed(!isLarge);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isLarge]);

  useEffect(() => {
    try {
      localStorage.setItem(COLLAPSE_KEY, collapsed ? '1' : '0');
    } catch {
      /* ignore */
    }
  }, [collapsed]);

  useEffect(() => setMobileOpen(false), [location.pathname]);

  const items = useMemo(() => NAV.filter((n) => !n.perm || can(n.perm)), [can]);
  const permWidth = collapsed ? NAV_MINI : NAV_FULL;
  const currentLabel =
    items.find((n) => (n.to === '/' ? location.pathname === '/' : location.pathname.startsWith(n.to)))?.label ?? '';

  const initials = (user?.fullName ?? '?')
    .split(/\s+/)
    .slice(0, 2)
    .map((s) => s[0]?.toUpperCase())
    .join('');

  const onMenuClick = () => (isMobile ? setMobileOpen(true) : setCollapsed((c) => !c));

  const drawerContent = (mini: boolean) => (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <Box
        sx={{
          minHeight: 64,
          px: mini ? 0 : 2,
          py: 1,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          flexShrink: 0,
        }}
      >
        <Box
          component="img"
          src="/logo.png"
          alt="Sasi Dairy Farm"
          onError={(e) => {
            (e.currentTarget as HTMLImageElement).style.display = 'none';
            (e.currentTarget.nextElementSibling as HTMLElement | null)?.style.setProperty('display', 'inline');
          }}
          sx={{ height: mini ? 40 : 52, width: 'auto', display: 'block' }}
        />
        <Typography color="primary" sx={{ display: 'none', fontWeight: 700, lineHeight: 1.1 }}>
          SASI
        </Typography>
      </Box>
      <Divider />
      <List sx={{ flex: 1, minHeight: 0, overflowY: 'auto', overflowX: 'hidden', py: 1 }}>
        {items.map((n) => {
          const selected = n.to === '/' ? location.pathname === '/' : location.pathname.startsWith(n.to);
          return (
            <Tooltip key={n.to} title={mini ? n.label : ''} placement="right" arrow>
              <ListItemButton
                component={RouterLink}
                to={n.to}
                selected={selected}
                sx={{
                  borderRadius: 2,
                  mx: mini ? 0.75 : 1,
                  mb: 0.5,
                  minHeight: 46,
                  justifyContent: mini ? 'center' : 'flex-start',
                  px: mini ? 1 : 2,
                }}
              >
                <ListItemIcon sx={{ minWidth: 0, mr: mini ? 0 : 2, justifyContent: 'center' }}>
                  {n.icon}
                </ListItemIcon>
                {!mini && <ListItemText primary={n.label} />}
              </ListItemButton>
            </Tooltip>
          );
        })}
      </List>
      <Divider />
      <Box sx={{ p: mini ? 1 : 2, flexShrink: 0, display: 'flex', alignItems: 'center', gap: 1.5, justifyContent: mini ? 'center' : 'flex-start' }}>
        <Avatar sx={{ width: 32, height: 32, bgcolor: 'primary.main', fontSize: 13 }}>{initials}</Avatar>
        {!mini && (
          <Box sx={{ minWidth: 0 }}>
            <Typography variant="body2" fontWeight={600} noWrap>
              {user?.fullName}
            </Typography>
            <Typography variant="caption" color="text.secondary" noWrap>
              {user?.username}
            </Typography>
          </Box>
        )}
      </Box>
    </Box>
  );

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      <AppBar
        position="fixed"
        color="inherit"
        elevation={0}
        sx={{
          width: { sm: `calc(100% - ${permWidth}px)` },
          ml: { sm: `${permWidth}px` },
          borderBottom: 1,
          borderColor: 'divider',
          transition: theme.transitions.create(['width', 'margin'], { duration: theme.transitions.duration.shorter }),
        }}
      >
        <Toolbar>
          <IconButton edge="start" onClick={onMenuClick} sx={{ mr: 1 }} aria-label="toggle navigation">
            {isMobile || collapsed ? <MenuIcon /> : <MenuOpenIcon />}
          </IconButton>
          <Box
            component="img"
            src="/logo.png"
            alt=""
            onError={(e) => ((e.currentTarget as HTMLImageElement).style.display = 'none')}
            sx={{ display: { xs: 'block', sm: 'none' }, height: 30, mr: 1 }}
          />
          <Typography variant="h6" sx={{ flex: 1 }} noWrap>
            {currentLabel}
          </Typography>

          {pending > 0 && (
            <Tooltip title={online ? 'Tap to sync now' : 'Offline — will sync when back online'}>
              <Chip
                size="small"
                color={online ? 'warning' : 'default'}
                icon={online ? <CloudDoneIcon /> : <CloudOffIcon />}
                label={syncing ? 'Syncing…' : `${pending}`}
                onClick={() => online && sync()}
                sx={{ mr: 1 }}
              />
            </Tooltip>
          )}
          {pending === 0 && !online && <Chip size="small" icon={<CloudOffIcon />} label="Offline" sx={{ mr: 1 }} />}

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

      <Box component="nav" sx={{ width: { sm: permWidth }, flexShrink: { sm: 0 } }}>
        {/* mobile: overlay drawer, sensible width, closes on selection */}
        <Drawer
          variant="temporary"
          open={mobileOpen}
          onClose={() => setMobileOpen(false)}
          ModalProps={{ keepMounted: true }}
          sx={{
            display: { xs: 'block', sm: 'none' },
            '& .MuiDrawer-paper': { width: 'min(280px, 82vw)' },
          }}
        >
          {drawerContent(false)}
        </Drawer>
        {/* tablet + desktop: permanent, collapsible to an icon rail */}
        <Drawer
          variant="permanent"
          open
          sx={{
            display: { xs: 'none', sm: 'block' },
            '& .MuiDrawer-paper': {
              width: permWidth,
              overflowX: 'hidden',
              borderRight: 1,
              borderColor: 'divider',
              transition: theme.transitions.create('width', { duration: theme.transitions.duration.shorter }),
            },
          }}
        >
          {drawerContent(collapsed)}
        </Drawer>
      </Box>

      <Box
        component="main"
        sx={{
          flexGrow: 1,
          width: { sm: `calc(100% - ${permWidth}px)` },
          p: { xs: 2, sm: 3 },
          transition: theme.transitions.create('width', { duration: theme.transitions.duration.shorter }),
        }}
      >
        <Toolbar />
        <Outlet />
      </Box>
    </Box>
  );
}
