import { Navigate, Route, Routes } from 'react-router-dom';
import { Loading, ToastProvider } from './components/ui';
import { AppLayout } from './components/AppLayout';
import { useAuth } from './lib/auth';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { CollectionPage } from './pages/CollectionPage';
import { ProducersPage } from './pages/ProducersPage';
import { ProducerDetailPage } from './pages/ProducerDetailPage';
import { RateChartsPage } from './pages/RateChartsPage';
import { IncentivesPage } from './pages/IncentivesPage';
import { DeductionsPage } from './pages/DeductionsPage';
import { AdvancesPage } from './pages/AdvancesPage';
import { SettlementsPage } from './pages/SettlementsPage';
import { SettlementDetailPage } from './pages/SettlementDetailPage';
import { ReportsPage } from './pages/ReportsPage';
import { VehiclesPage } from './pages/VehiclesPage';
import { NotificationsPage } from './pages/NotificationsPage';
import { CenterPage } from './pages/CenterPage';

export function App() {
  const { user, initializing } = useAuth();

  if (initializing) return <Loading label="Starting…" />;

  return (
    <ToastProvider>
      <Routes>
        <Route path="/login" element={user ? <Navigate to="/" replace /> : <LoginPage />} />
        <Route element={user ? <AppLayout /> : <Navigate to="/login" replace />}>
          <Route index element={<DashboardPage />} />
          <Route path="collection" element={<CollectionPage />} />
          <Route path="producers" element={<ProducersPage />} />
          <Route path="producers/:id" element={<ProducerDetailPage />} />
          <Route path="rates" element={<RateChartsPage />} />
          <Route path="incentives" element={<IncentivesPage />} />
          <Route path="deductions" element={<DeductionsPage />} />
          <Route path="advances" element={<AdvancesPage />} />
          <Route path="settlements" element={<SettlementsPage />} />
          <Route path="settlements/:id" element={<SettlementDetailPage />} />
          <Route path="reports" element={<ReportsPage />} />
          <Route path="vehicles" element={<VehiclesPage />} />
          <Route path="notifications" element={<NotificationsPage />} />
          <Route path="center" element={<CenterPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </ToastProvider>
  );
}
