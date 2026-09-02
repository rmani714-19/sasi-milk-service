import { useEffect, useMemo, useState, type MouseEvent } from 'react';
import { Link as RouterLink, Outlet, useLocation } from 'react-router-dom';
import AppBar from '@mui/material/AppBar';
import Avatar from '@mui/material/Avatar';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import Drawer from '@mui/material/Drawer';
import IconButton from '@mui/material/IconButton';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import ListSubheader from '@mui/material/ListSubheader';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Toolbar from '@mui/material/Toolbar';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useTheme } from '@mui/material/styles';
import MenuIcon from '@mui/icons-material/Menu';
import ArrowDropDownIcon from '@mui/icons-material/ArrowDropDown';
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
import AccountBalanceWalletIcon from '@mui/icons-material/AccountBalanceWallet';
import TuneIcon from '@mui/icons-material/Tune';
import { useAuth } from '../lib/auth';
import { useAppTheme } from '../lib/appTheme';
import { usePendingSync } from '../lib/usePendingSync';

interface Leaf {
  to: string;
  label: string;
  icon: JSX.Element;
  perm?: string;
}
type NavNode =
  | ({ kind: 'item' } & Leaf)
  | { kind: 'menu'; label: string; icon: JSX.Element; children: Leaf[] };

/** Grouped so the header stays readable — flat items + two dropdown menus. */
const NAV: NavNode[] = [
  { kind: 'item', to: '/', label: 'Dashboard', icon: <DashboardIcon />, perm: 'VIEW_DASHBOARD' },
  { kind: 'item', to: '/collection', label: 'Milk Collection', icon: <LocalDrinkIcon />, perm: 'CREATE_COLLECTION' },
  { kind: 'item', to: '/producers', label: 'Farmers', icon: <AgricultureIcon />, perm: 'VIEW_PRODUCERS' },
  {
    kind: 'menu',
    label: 'Finance',
    icon: <AccountBalanceWalletIcon />,
    children: [
      { to: '/rates', label: 'Milk Rate', icon: <PriceChangeIcon />, perm: 'MANAGE_RATES' },
      { to: '/incentives', label: 'Incentives', icon: <RedeemIcon />, perm: 'MANAGE_INCENTIVES' },
      { to: '/deductions', label: 'Deductions', icon: <RemoveCircleIcon />, perm: 'MANAGE_DEDUCTIONS' },
      { to: '/advances', label: 'Advances', icon: <SavingsIcon />, perm: 'MANAGE_ADVANCES' },
      { to: '/settlements', label: 'Payments', icon: <ReceiptLongIcon />, perm: 'GENERATE_SETTLEMENT' },
    ],
  },
  { kind: 'item', to: '/reports', label: 'Reports', icon: <AssessmentIcon />, perm: 'VIEW_REPORTS' },
  {
    kind: 'menu',
    label: 'Setup',
    icon: <TuneIcon />,
    children: [
      { to: '/vehicles', label: 'Vehicles', icon: <LocalShippingIcon />, perm: 'VIEW_DASHBOARD' },
      { to: '/notifications', label: 'Notifications', icon: <NotificationsIcon />, perm: 'ALL' },
      { to: '/center', label: 'Centre', icon: <StoreIcon />, perm: 'MANAGE_RATES' },
    ],
  },
];

const isActive = (to: string, pathname: string) =>
  to === '/' ? pathname === '/' : pathname === to || pathname.startsWith(`${to}/`);

const navButtonSx = (active: boolean) => ({
  color: 'inherit',
  minHeight: 64,
  px: 1.5,
  borderRadius: 0,
  borderBottom: '2px solid',
  borderColor: active ? 'currentColor' : 'transparent',
  fontWeight: active ? 700 : 500,
  whiteSpace: 'nowrap',
  '& .MuiButton-startIcon': { mr: 0.75 },
  '&:hover': { bgcolor: 'action.hover' },
});

function NavMenu({ label, icon, items, pathname }: { label: string; icon: JSX.Element; items: Leaf[]; pathname: string }) {
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const active = items.some((c) => isActive(c.to, pathname));
  return (
    <>
      <Button
        startIcon={icon}
        endIcon={<ArrowDropDownIcon />}
        onClick={(e: MouseEvent<HTMLElement>) => setAnchor(e.currentTarget)}
        sx={navButtonSx(active)}
      >
        {label}
      </Button>
      <Menu
        anchorEl={anchor}
        open={!!anchor}
        onClose={() => setAnchor(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}
        transformOrigin={{ vertical: 'top', horizontal: 'left' }}
      >
        {items.map((c) => (
          <MenuItem
            key={c.to}
            component={RouterLink}
            to={c.to}
            selected={isActive(c.to, pathname)}
            onClick={() => setAnchor(null)}
          >
            <ListItemIcon>{c.icon}</ListItemIcon>
            <ListItemText>{c.label}</ListItemText>
          </MenuItem>
        ))}
      </Menu>
    </>
  );
}

export function AppLayout() {
  const { user, logout, can } = useAuth();
  const { mode, auto, toggle } = useAppTheme();
  const theme = useTheme();
  const location = useLocation();
  const pathname = location.pathname;
  const { pending, online, syncing, sync } = usePendingSync();

  const isCompact = useMediaQuery(theme.breakpoints.down('md')); // < 900px → hamburger + drawer
  const [mobileOpen, setMobileOpen] = useState(false);
  const [userAnchor, setUserAnchor] = useState<HTMLElement | null>(null);

  useEffect(() => setMobileOpen(false), [pathname]);

  const nodes = useMemo<NavNode[]>(() => {
    const out: NavNode[] = [];
    for (const n of NAV) {
      if (n.kind === 'item') {
        if (!n.perm || can(n.perm)) out.push(n);
      } else {
        const children = n.children.filter((c) => !c.perm || can(c.perm));
        if (children.length) out.push({ ...n, children });
      }
    }
    return out;
  }, [can]);

  const leaves = useMemo(
    () => nodes.flatMap((n) => (n.kind === 'item' ? [n as Leaf] : n.children)),
    [nodes],
  );
  const currentLabel = leaves.find((l) => isActive(l.to, pathname))?.label ?? '';

  const initials = (user?.fullName ?? '?')
    .split(/\s+/)
    .slice(0, 2)
    .map((s) => s[0]?.toUpperCase())
    .join('');

  const drawerNav = (
    <Box sx={{ width: 'min(280px, 82vw)' }} role="navigation">
      <Box sx={{ minHeight: 64, px: 2, display: 'flex', alignItems: 'center' }}>
        <Box
          component="img"
          src="/logo.png"
          alt="Sasi Dairy Farm"
          onError={(e) => {
            (e.currentTarget as HTMLImageElement).style.display = 'none';
            (e.currentTarget.nextElementSibling as HTMLElement | null)?.style.setProperty('display', 'inline');
          }}
          sx={{ height: 40, width: 'auto', display: 'block' }}
        />
        <Typography color="primary" sx={{ display: 'none', fontWeight: 700 }}>
          SASI
        </Typography>
      </Box>
      <Divider />
      <List>
        {nodes.map((n) =>
          n.kind === 'item' ? (
            <ListItemButton
              key={n.to}
              component={RouterLink}
              to={n.to}
              selected={isActive(n.to, pathname)}
              sx={{ borderRadius: 2, mx: 1, mb: 0.5 }}
            >
              <ListItemIcon sx={{ minWidth: 40 }}>{n.icon}</ListItemIcon>
              <ListItemText primary={n.label} />
            </ListItemButton>
          ) : (
            <Box key={n.label}>
              <ListSubheader disableSticky sx={{ bgcolor: 'transparent', lineHeight: '32px' }}>
                {n.label}
              </ListSubheader>
              {n.children.map((c) => (
                <ListItemButton
                  key={c.to}
                  component={RouterLink}
                  to={c.to}
                  selected={isActive(c.to, pathname)}
                  sx={{ borderRadius: 2, mx: 1, mb: 0.5 }}
                >
                  <ListItemIcon sx={{ minWidth: 40 }}>{c.icon}</ListItemIcon>
                  <ListItemText primary={c.label} />
                </ListItemButton>
              ))}
            </Box>
          ),
        )}
      </List>
      <Divider />
      <Box sx={{ p: 2, display: 'flex', alignItems: 'center', gap: 1.5 }}>
        <Avatar sx={{ width: 32, height: 32, bgcolor: 'primary.main', fontSize: 13 }}>{initials}</Avatar>
        <Box sx={{ minWidth: 0 }}>
          <Typography variant="body2" fontWeight={600} noWrap>
            {user?.fullName}
          </Typography>
          <Typography variant="caption" color="text.secondary" noWrap>
            {user?.username}
          </Typography>
        </Box>
      </Box>
    </Box>
  );

  return (
    <Box sx={{ minHeight: '100vh' }}>
      <AppBar position="fixed" color="inherit" elevation={0} sx={{ borderBottom: 1, borderColor: 'divider' }}>
        <Toolbar sx={{ gap: 0.5 }}>
          {isCompact && (
            <IconButton edge="start" onClick={() => setMobileOpen(true)} aria-label="open navigation" sx={{ mr: 0.5 }}>
              <MenuIcon />
            </IconButton>
          )}
          <Box
            component="img"
            src="/logo.png"
            alt="Sasi Dairy Farm"
            onError={(e) => ((e.currentTarget as HTMLImageElement).style.display = 'none')}
            sx={{ height: 36, width: 'auto', mr: 1, flexShrink: 0 }}
          />

          {isCompact ? (
            <Typography variant="h6" noWrap sx={{ flex: 1 }}>
              {currentLabel}
            </Typography>
          ) : (
            <Box sx={{ display: 'flex', alignItems: 'stretch', flex: 1, ml: 1, overflowX: 'auto' }}>
              {nodes.map((n) =>
                n.kind === 'item' ? (
                  <Button
                    key={n.to}
                    component={RouterLink}
                    to={n.to}
                    startIcon={n.icon}
                    sx={navButtonSx(isActive(n.to, pathname))}
                  >
                    {n.label}
                  </Button>
                ) : (
                  <NavMenu key={n.label} label={n.label} icon={n.icon} items={n.children} pathname={pathname} />
                ),
              )}
            </Box>
          )}

          {pending > 0 && (
            <Tooltip title={online ? 'Tap to sync now' : 'Offline — will sync when back online'}>
              <Chip
                size="small"
                color={online ? 'warning' : 'default'}
                icon={online ? <CloudDoneIcon /> : <CloudOffIcon />}
                label={syncing ? 'Syncing…' : `${pending}`}
                onClick={() => online && sync()}
                sx={{ mr: 0.5 }}
              />
            </Tooltip>
          )}
          {pending === 0 && !online && <Chip size="small" icon={<CloudOffIcon />} label="Offline" sx={{ mr: 0.5 }} />}

          <Tooltip title={`${mode === 'morning' ? 'Morning' : 'Evening'} theme${auto ? ' (auto)' : ''} — tap to switch`}>
            <IconButton color="inherit" onClick={toggle}>
              {mode === 'morning' ? <WbSunnyIcon /> : <DarkModeIcon />}
            </IconButton>
          </Tooltip>

          <Tooltip title={user?.fullName ?? 'Account'}>
            <IconButton onClick={(e) => setUserAnchor(e.currentTarget)} sx={{ ml: 0.5 }} aria-label="account menu">
              <Avatar sx={{ width: 32, height: 32, bgcolor: 'primary.main', fontSize: 13 }}>{initials}</Avatar>
            </IconButton>
          </Tooltip>
          <Menu
            anchorEl={userAnchor}
            open={!!userAnchor}
            onClose={() => setUserAnchor(null)}
            anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
            transformOrigin={{ vertical: 'top', horizontal: 'right' }}
          >
            <Box sx={{ px: 2, py: 1 }}>
              <Typography variant="body2" fontWeight={600}>
                {user?.fullName}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {user?.username}
              </Typography>
            </Box>
            <Divider />
            <MenuItem
              onClick={() => {
                setUserAnchor(null);
                logout();
              }}
            >
              <ListItemIcon>
                <LogoutIcon fontSize="small" />
              </ListItemIcon>
              <ListItemText>Sign out</ListItemText>
            </MenuItem>
          </Menu>
        </Toolbar>
      </AppBar>

      <Drawer
        variant="temporary"
        open={mobileOpen}
        onClose={() => setMobileOpen(false)}
        ModalProps={{ keepMounted: true }}
        sx={{ display: { md: 'none' }, '& .MuiDrawer-paper': { width: 'min(280px, 82vw)' } }}
      >
        {drawerNav}
      </Drawer>

      <Box component="main" sx={{ p: { xs: 2, md: 3 } }}>
        <Toolbar />
        <Outlet />
      </Box>
    </Box>
  );
}
