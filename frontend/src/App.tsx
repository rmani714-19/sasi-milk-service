import { Navigate, Route, Routes } from 'react-router-dom';
import { Loading, ToastProvider } from './components/ui';
import { AppLayout } from './components/AppLayout';
import { useAuth } from './lib/auth';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { CollectionPage } from './pages/CollectionPage';
import { CollectionsPage } from './pages/CollectionsPage';
import { ProducersPage } from './pages/ProducersPage';
import { ProducerDetailPage } from './pages/ProducerDetailPage';
import { RegionsPage } from './pages/RegionsPage';
import { RateChartsPage } from './pages/RateChartsPage';
import { IncentivesPage } from './pages/IncentivesPage';
import { DeductionsPage } from './pages/DeductionsPage';
import { AdvancesPage } from './pages/AdvancesPage';
import { SettlementsPage } from './pages/SettlementsPage';
import { SettlementDetailPage } from './pages/SettlementDetailPage';
import { WeeklyFarmerPaymentPage, MonthlyFarmerPaymentPage } from './pages/payments/FarmerPaymentsPage';
import { PaymentDashboardPage } from './pages/payments/PaymentDashboardPage';
import { MonthlyVerificationPage, WeeklyVerificationPage } from './pages/payments/ProducerVerificationPage';
import { DriverPaymentsPage } from './pages/payments/DriverPaymentsPage';
import { PaymentHistoryPage } from './pages/payments/PaymentHistoryPage';
import { PaymentReportsPage } from './pages/payments/PaymentReportsPage';
import { ReportsPage } from './pages/ReportsPage';
import { VehicleMasterPage } from './pages/vehicles/VehicleMasterPage';
import { VehicleDetailPage } from './pages/vehicles/VehicleDetailPage';
import { LiveTrackingPage } from './pages/vehicles/LiveTrackingPage';
import { VehicleAlertsPage } from './pages/vehicles/VehicleAlertsPage';
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
          <Route path="collections" element={<CollectionsPage />} />
          <Route path="producers" element={<ProducersPage />} />
          <Route path="producers/:id" element={<ProducerDetailPage />} />
          <Route path="regions" element={<RegionsPage />} />
          <Route path="rates" element={<RateChartsPage />} />
          <Route path="incentives" element={<IncentivesPage />} />
          <Route path="deductions" element={<DeductionsPage />} />
          <Route path="advances" element={<AdvancesPage />} />
          <Route path="settlements" element={<SettlementsPage />} />
          <Route path="settlements/:id" element={<SettlementDetailPage />} />
          <Route path="payments/dashboard" element={<PaymentDashboardPage />} />
          <Route path="payments/weekly/verify" element={<WeeklyVerificationPage />} />
          <Route path="payments/monthly/verify" element={<MonthlyVerificationPage />} />
          <Route path="payments/weekly" element={<WeeklyFarmerPaymentPage />} />
          <Route path="payments/monthly" element={<MonthlyFarmerPaymentPage />} />
          <Route path="payments/farmers" element={<Navigate to="/payments/weekly" replace />} />
          <Route path="payments/drivers" element={<DriverPaymentsPage />} />
          <Route path="payments/history" element={<PaymentHistoryPage />} />
          <Route path="payments/reports" element={<PaymentReportsPage />} />
          <Route path="reports" element={<ReportsPage />} />
          <Route path="vehicles" element={<VehicleMasterPage />} />
          <Route path="vehicles/tracking" element={<LiveTrackingPage />} />
          <Route path="vehicles/alerts" element={<VehicleAlertsPage />} />
          <Route path="vehicles/:vehicleId" element={<VehicleDetailPage />} />
          <Route path="notifications" element={<NotificationsPage />} />
          <Route path="center" element={<CenterPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </ToastProvider>
  );
}
