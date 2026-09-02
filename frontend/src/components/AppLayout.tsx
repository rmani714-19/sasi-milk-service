import { useEffect, useMemo, useState } from 'react';
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
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
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
import WbSunnyOutlinedIcon from '@mui/icons-material/WbSunnyOutlined';
import DarkModeOutlinedIcon from '@mui/icons-material/DarkModeOutlined';
import DashboardOutlinedIcon from '@mui/icons-material/DashboardOutlined';
import LocalDrinkOutlinedIcon from '@mui/icons-material/LocalDrinkOutlined';
import PeopleOutlinedIcon from '@mui/icons-material/PeopleOutlined';
import CardGiftcardOutlinedIcon from '@mui/icons-material/CardGiftcardOutlined';
import ReceiptLongOutlinedIcon from '@mui/icons-material/ReceiptLongOutlined';
import AccountBalanceWalletOutlinedIcon from '@mui/icons-material/AccountBalanceWalletOutlined';
import TableChartOutlinedIcon from '@mui/icons-material/TableChartOutlined';
import PaymentsOutlinedIcon from '@mui/icons-material/PaymentsOutlined';
import AssessmentOutlinedIcon from '@mui/icons-material/AssessmentOutlined';
import LocalShippingOutlinedIcon from '@mui/icons-material/LocalShippingOutlined';
import BusinessOutlinedIcon from '@mui/icons-material/BusinessOutlined';
import PlaceOutlinedIcon from '@mui/icons-material/PlaceOutlined';
import NotificationsNoneOutlinedIcon from '@mui/icons-material/NotificationsNoneOutlined';
import { useAuth } from '../lib/auth';
import { useAppTheme } from '../lib/appTheme';
import { usePendingSync } from '../lib/usePendingSync';
import { MODE_STYLES, PALETTE } from '../theme';

const NAV_FULL = 250;
const NAV_MINI = 72;
const COLLAPSE_KEY = 'sasi.navCollapsed';

interface NavItem {
  to: string;
  label: string;
  icon: JSX.Element;
  perm?: string;
}

const NAV: NavItem[] = [
  { to: '/', label: 'Dashboard', icon: <DashboardOutlinedIcon />, perm: 'VIEW_DASHBOARD' },
  { to: '/collection', label: 'Milk Collection', icon: <LocalDrinkOutlinedIcon />, perm: 'CREATE_COLLECTION' },
  { to: '/producers', label: 'Producers', icon: <PeopleOutlinedIcon />, perm: 'VIEW_PRODUCERS' },
  { to: '/regions', label: 'Regions', icon: <PlaceOutlinedIcon />, perm: 'MANAGE_RATES' },
  { to: '/incentives', label: 'Incentives', icon: <CardGiftcardOutlinedIcon />, perm: 'MANAGE_INCENTIVES' },
  { to: '/deductions', label: 'Deductions', icon: <ReceiptLongOutlinedIcon />, perm: 'MANAGE_DEDUCTIONS' },
  { to: '/advances', label: 'Advances', icon: <AccountBalanceWalletOutlinedIcon />, perm: 'MANAGE_ADVANCES' },
  { to: '/rates', label: 'Rate Chart', icon: <TableChartOutlinedIcon />, perm: 'MANAGE_RATES' },
  { to: '/settlements', label: 'Settlement', icon: <PaymentsOutlinedIcon />, perm: 'GENERATE_SETTLEMENT' },
  { to: '/reports', label: 'Reports', icon: <AssessmentOutlinedIcon />, perm: 'VIEW_REPORTS' },
  { to: '/vehicles', label: 'Vehicles', icon: <LocalShippingOutlinedIcon />, perm: 'VIEW_DASHBOARD' },
  { to: '/center', label: 'My Center', icon: <BusinessOutlinedIcon />, perm: 'MANAGE_RATES' },
  { to: '/notifications', label: 'Notifications', icon: <NotificationsNoneOutlinedIcon />, perm: 'ALL' },
];

const isActive = (to: string, pathname: string) =>
  to === '/' ? pathname === '/' : pathname === to || pathname.startsWith(`${to}/`);

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
  const pathname = location.pathname;
  const { pending, online, syncing, sync } = usePendingSync();

  const isMobile = useMediaQuery(theme.breakpoints.down('md')); // < 900px → temporary drawer
  const isLarge = useMediaQuery(theme.breakpoints.up('lg'));

  const [mobileOpen, setMobileOpen] = useState(false);
  const [collapsed, setCollapsed] = useState<boolean>(() => readCollapsed() ?? false);
  const [userAnchor, setUserAnchor] = useState<HTMLElement | null>(null);

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

  useEffect(() => setMobileOpen(false), [pathname]);

  const items = useMemo(() => NAV.filter((n) => !n.perm || can(n.perm)), [can]);
  const railWidth = collapsed ? NAV_MINI : NAV_FULL;
  const currentLabel = items.find((n) => isActive(n.to, pathname))?.label ?? '';

  const initials = (user?.fullName ?? '?')
    .split(/\s+/)
    .slice(0, 2)
    .map((s) => s[0]?.toUpperCase())
    .join('');

  const onMenuClick = () => (isMobile ? setMobileOpen(true) : setCollapsed((c) => !c));

  const drawerContent = (mini: boolean) => (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%', bgcolor: 'background.paper' }}>
      <Box
        sx={{
          minHeight: 64,
          px: mini ? 0 : 2.5,
          display: 'flex',
          alignItems: 'center',
          justifyContent: mini ? 'center' : 'flex-start',
          gap: 1.25,
          flexShrink: 0,
        }}
      >
        <Box
          component="img"
          src="/logo.png"
          alt="Sasi Dairy Farm"
          onError={(e) => {
            (e.currentTarget as HTMLImageElement).style.display = 'none';
            (e.currentTarget.nextElementSibling as HTMLElement | null)?.style.setProperty('display', 'block');
          }}
          sx={{ height: mini ? 34 : 40, width: 'auto', display: 'block', flexShrink: 0 }}
        />
        {!mini && (
          <Typography
            sx={{ fontWeight: 700, fontSize: '0.9375rem', color: 'primary.dark', lineHeight: 1.15 }}
          >
            SASI MILK
            <br />
            SERVICE
          </Typography>
        )}
      </Box>
      <Divider />

      <List sx={{ flex: 1, minHeight: 0, overflowY: 'auto', overflowX: 'hidden', px: mini ? 0.75 : 1.25, py: 1.5 }}>
        {items.map((n) => {
          const active = isActive(n.to, pathname);
          return (
            <Tooltip key={n.to} title={mini ? n.label : ''} placement="right" arrow>
              <ListItemButton
                component={RouterLink}
                to={n.to}
                disableRipple
                sx={{
                  borderRadius: '10px',
                  minHeight: 44,
                  mb: 0.5,
                  px: mini ? 0 : 1.5,
                  justifyContent: mini ? 'center' : 'flex-start',
                  color: active ? 'primary.main' : 'text.secondary',
                  bgcolor: active ? PALETTE.primaryLight : 'transparent',
                  fontWeight: active ? 600 : 500,
                  '&:hover': { bgcolor: active ? PALETTE.primaryLight : PALETTE.surfaceAlt },
                }}
              >
                <ListItemIcon
                  sx={{
                    minWidth: 0,
                    mr: mini ? 0 : 1.75,
                    justifyContent: 'center',
                    color: 'inherit',
                    '& svg': { fontSize: 22 },
                  }}
                >
                  {n.icon}
                </ListItemIcon>
                {!mini && (
                  <ListItemText
                    primary={n.label}
                    primaryTypographyProps={{ fontSize: '0.875rem', fontWeight: 'inherit' }}
                  />
                )}
              </ListItemButton>
            </Tooltip>
          );
        })}
      </List>

      <Divider />
      <Box
        sx={{
          p: mini ? 1 : 2,
          display: 'flex',
          alignItems: 'center',
          gap: 1.5,
          justifyContent: mini ? 'center' : 'flex-start',
          flexShrink: 0,
        }}
      >
        <Avatar sx={{ width: 34, height: 34, bgcolor: 'primary.main', fontSize: 13, fontWeight: 700 }}>
          {initials}
        </Avatar>
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
    <Box sx={{ display: 'flex', minHeight: '100vh', bgcolor: 'background.default' }}>
      <AppBar
        position="fixed"
        color="inherit"
        elevation={0}
        sx={{
          width: { md: `calc(100% - ${railWidth}px)` },
          ml: { md: `${railWidth}px` },
          transition: theme.transitions.create(['width', 'margin'], {
            duration: theme.transitions.duration.shorter,
          }),
        }}
      >
        <Toolbar sx={{ gap: 1, minHeight: 64 }}>
          <IconButton edge="start" onClick={onMenuClick} aria-label="toggle navigation" color="inherit">
            {isMobile || collapsed ? <MenuIcon /> : <MenuOpenIcon />}
          </IconButton>
          <Box
            component="img"
            src="/logo.png"
            alt=""
            onError={(e) => ((e.currentTarget as HTMLImageElement).style.display = 'none')}
            sx={{ display: { xs: 'block', md: 'none' }, height: 30, mr: 0.5 }}
          />
          <Typography variant="h6" sx={{ flex: 1, fontWeight: 600 }} noWrap>
            {currentLabel}
          </Typography>

          {pending > 0 && (
            <Tooltip title={online ? 'Tap to sync now' : 'Offline — will sync when back online'}>
              <Chip
                size="small"
                color={online ? 'warning' : 'default'}
                icon={online ? <CloudDoneIcon /> : <CloudOffIcon />}
                label={syncing ? 'Syncing…' : `${pending} queued`}
                onClick={() => online && sync()}
              />
            </Tooltip>
          )}
          {pending === 0 && !online && <Chip size="small" icon={<CloudOffIcon />} label="Offline" />}

          {can('ALL') && (
            <Tooltip title="Notifications">
              <IconButton component={RouterLink} to="/notifications" color="inherit" aria-label="notifications">
                <NotificationsNoneOutlinedIcon />
              </IconButton>
            </Tooltip>
          )}

          <Tooltip
            title={
              auto
                ? `Auto — following the clock (${mode}). Tap to lock the other shift skin.`
                : `${MODE_STYLES[mode].label} skin locked. Tap to switch.`
            }
          >
            <Button
              onClick={toggle}
              aria-label="switch shift theme"
              startIcon={mode === 'morning' ? <WbSunnyOutlinedIcon /> : <DarkModeOutlinedIcon />}
              sx={{
                borderRadius: 999,
                px: 1.5,
                minHeight: 34,
                bgcolor: MODE_STYLES[mode].badgeBg,
                color: MODE_STYLES[mode].badgeText,
                fontWeight: 700,
                '&:hover': { bgcolor: MODE_STYLES[mode].badgeBg, filter: 'brightness(0.96)' },
                '& .MuiButton-startIcon': { mr: 0.5 },
              }}
            >
              <Box component="span" sx={{ display: { xs: 'none', sm: 'inline' } }}>
                {mode === 'morning' ? 'Morning' : 'Evening'}
              </Box>
            </Button>
          </Tooltip>

          <Tooltip title={user?.fullName ?? 'Account'}>
            <IconButton onClick={(e) => setUserAnchor(e.currentTarget)} sx={{ ml: 0.25 }} aria-label="account menu">
              <Avatar sx={{ width: 32, height: 32, bgcolor: 'primary.main', fontSize: 13, fontWeight: 700 }}>
                {initials}
              </Avatar>
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

      <Box component="nav" sx={{ width: { md: railWidth }, flexShrink: { md: 0 } }}>
        <Drawer
          variant="temporary"
          open={mobileOpen}
          onClose={() => setMobileOpen(false)}
          ModalProps={{ keepMounted: true }}
          sx={{
            display: { xs: 'block', md: 'none' },
            '& .MuiDrawer-paper': { width: 'min(280px, 82vw)', borderRight: `1px solid ${PALETTE.border}` },
          }}
        >
          {drawerContent(false)}
        </Drawer>
        <Drawer
          variant="permanent"
          open
          sx={{
            display: { xs: 'none', md: 'block' },
            '& .MuiDrawer-paper': {
              width: railWidth,
              overflowX: 'hidden',
              borderRight: `1px solid ${PALETTE.border}`,
              transition: theme.transitions.create('width', {
                duration: theme.transitions.duration.shorter,
              }),
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
          width: { md: `calc(100% - ${railWidth}px)` },
          p: { xs: 2, md: 3 },
          transition: theme.transitions.create('width', { duration: theme.transitions.duration.shorter }),
        }}
      >
        <Toolbar sx={{ minHeight: 64 }} />
        <Outlet />
      </Box>
    </Box>
  );
}
